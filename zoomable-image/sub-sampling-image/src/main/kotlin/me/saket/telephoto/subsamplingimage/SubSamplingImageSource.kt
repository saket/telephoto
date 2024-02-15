package me.saket.telephoto.subsamplingimage

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetManager
import android.graphics.BitmapRegionDecoder
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import okio.BufferedSource
import okio.Closeable
import okio.Path
import okio.Source
import okio.buffer
import java.io.InputStream

/**
 * Image to display with [SubSamplingImage]. Can be one of:
 *
 * * [SubSamplingImageSource.file]
 * * [SubSamplingImageSource.asset]
 * * [SubSamplingImageSource.resource]
 * * [SubSamplingImageSource.contentUri]
 * * [SubSamplingImageSource.rawSource]
 */
sealed interface SubSamplingImageSource : Closeable {
  /**
   * A preview that can be displayed immediately while the bitmap tiles
   * are loaded, which can be slightly slow depending on the file size.
   */
  val preview: ImageBitmap?

  companion object {
    /**
     * An image stored on the device file system. This can be used with
     * image loading libraries that store cached images on disk.
     *
     * @param preview See [SubSamplingImageSource.preview].
     * @param onClose Called when the image is no longer visible. This is useful for files
     *                stored in, say, an LRU cache that is capable of locking open files to
     *                prevent them from getting discarded.
     *
     * @return The returned value is stable and does not need to be remembered.
     */
    @Stable
    fun file(
      path: Path,
      preview: ImageBitmap? = null,
      onClose: Closeable? = null
    ): SubSamplingImageSource = FileImageSource(path, preview, onClose)

    /**
     * An image stored in `src/main/assets`.
     *
     * @param preview See [SubSamplingImageSource.preview].
     *
     * @return The returned value is stable and does not need to be remembered.
     */
    @Stable
    fun asset(
      name: String,
      preview: ImageBitmap? = null
    ): SubSamplingImageSource = AssetImageSource(AssetPath(name), preview)

    /**
     * An image stored in `src/main/res/drawable*` directories.
     *
     * @param preview See [SubSamplingImageSource.preview].
     *
     * @return The returned value is stable and does not need to be remembered.
     * */
    @Stable
    fun resource(
      @DrawableRes id: Int,
      preview: ImageBitmap? = null
    ): SubSamplingImageSource = ResourceImageSource(id, preview)

    /**
     * An image exposed by a content provider. A common use-case for this
     * would be to display images shared by other apps.
     *
     * @param preview See [SubSamplingImageSource.preview].
     *
     * @return The returned value is stable and does not need to be remembered.
     */
    @Stable
    fun contentUri(
      uri: Uri,
      preview: ImageBitmap? = null
    ): SubSamplingImageSource {
      val assetPath = uri.asAssetPathOrNull()
      return if (assetPath != null) AssetImageSource(assetPath, preview) else UriImageSource(uri, preview)
    }

    /**
     * An arbitrary stream that should only be used for images that can't be read directly
     * from the disk. For all other purposes, prefer using [SubSamplingImageSource.file]
     * instead as it is significantly faster.
     *
     * @param preview See [SubSamplingImageSource.preview].
     * @param onClose Called when the image is no longer visible.
     */
    @Stable
    fun rawSource(
      source: () -> Source, // todo: should this be a BufferedSource?
      preview: ImageBitmap? = null,
      onClose: Closeable? = null,
    ): SubSamplingImageSource = RawImageSource(source, preview, onClose)
  }

  suspend fun decoder(context: Context): BitmapRegionDecoder

  /** Called when the image is no longer visible. */
  override fun close() = Unit
}

@Immutable
internal data class FileImageSource(
  val path: Path,
  override val preview: ImageBitmap?,
  val onClose: Closeable?
) : SubSamplingImageSource {
  init {
    check(path.isAbsolute)
  }

  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return ParcelFileDescriptor.open(path.toFile(), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
      @Suppress("DEPRECATION")
      BitmapRegionDecoder.newInstance(fd.fileDescriptor, /* ignored */ false)
    }
  }

  override fun close() {
    onClose?.close()
  }
}

@Immutable
internal data class AssetImageSource(
  private val asset: AssetPath,
  override val preview: ImageBitmap?
) : SubSamplingImageSource {

  fun peek(context: Context): InputStream {
    return context.assets.open(asset.path, AssetManager.ACCESS_RANDOM)
  }

  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return peek(context).use { stream ->
      check (stream is AssetManager.AssetInputStream) {
        error("BitmapRegionDecoder won't be able to optimize reading of this asset")
      }
      @Suppress("DEPRECATION")
      BitmapRegionDecoder.newInstance(stream, /* ignored */ false)!!
    }
  }
}

@Immutable
internal data class ResourceImageSource(
  @DrawableRes val id: Int,
  override val preview: ImageBitmap?,
) : SubSamplingImageSource {

  @SuppressLint("ResourceType")
  fun peek(context: Context): InputStream {
    return context.resources.openRawResource(id)
  }

  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return peek(context).use { stream ->
      @Suppress("DEPRECATION")
      BitmapRegionDecoder.newInstance(stream, /* ignored */ false)!!
    }
  }
}

@Immutable
internal data class UriImageSource(
  private val uri: Uri,
  override val preview: ImageBitmap?
) : SubSamplingImageSource {

  fun peek(context: Context): InputStream {
    return context.contentResolver.openInputStream(uri) ?: error("Failed to read uri: $uri")
  }

  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    @Suppress("DEPRECATION")
    return peek(context).use {
      stream -> BitmapRegionDecoder.newInstance(stream, /* ignored */ false)!!
    }
  }
}

@Immutable
internal data class RawImageSource(
  val source: () -> Source,
  override val preview: ImageBitmap? = null,
  private val onClose: Closeable?
) : SubSamplingImageSource {

  fun peek(): BufferedSource {
    return source().buffer().peek()
  }

  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return source().buffer().inputStream().use { stream ->
      @Suppress("DEPRECATION")
      BitmapRegionDecoder.newInstance(stream, /* ignored */ false)!!
    }
  }

  override fun close() {
    onClose?.close()
  }
}

@Immutable
@JvmInline
internal value class AssetPath(val path: String)

internal fun Uri.asAssetPathOrNull(): AssetPath? {
  val isAssetUri = scheme == ContentResolver.SCHEME_FILE && pathSegments.firstOrNull() == "android_asset"
  return if (isAssetUri) AssetPath(pathSegments.drop(1).joinToString("/")) else null
}
