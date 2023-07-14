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
import okio.Closeable
import okio.Path

/**
 * Image to display with [SubSamplingImage]. Can be one of:
 *
 * * [SubSamplingImageSource.file]
 * * [SubSamplingImageSource.asset]
 * * [SubSamplingImageSource.resource]
 * * [SubSamplingImageSource.contentUri]
 *
 * Raw input streams aren't supported because reading from files is significantly faster.
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
      BitmapRegionDecoder.newInstance(fd.fileDescriptor, /* ignored */ false)
    }
  }

  override fun close() {
    onClose?.close()
  }
}

@Immutable
internal data class AssetImageSource(
  val asset: AssetPath,
  override val preview: ImageBitmap?
) : SubSamplingImageSource {
  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return context.assets.open(asset.path, AssetManager.ACCESS_RANDOM).use { stream ->
      if (BuildConfig.DEBUG && stream !is AssetManager.AssetInputStream) {
        error("BitmapRegionDecoder won't be able to optimize reading of this asset")
      }
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
  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return context.resources.openRawResource(id).use { stream ->
      BitmapRegionDecoder.newInstance(stream, /* ignored */ false)!!
    }
  }
}

@Immutable
internal data class UriImageSource(
  val uri: Uri,
  override val preview: ImageBitmap?
) : SubSamplingImageSource {
  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return context.contentResolver.openInputStream(uri)
      ?.use { stream -> BitmapRegionDecoder.newInstance(stream, /* ignored */ false)!! }
      ?: error("Failed to read bitmap from uri: $uri")
  }
}

@Immutable
@JvmInline
internal value class AssetPath(val path: String)

internal fun Uri.asAssetPathOrNull(): AssetPath? {
  val isAssetUri = scheme == ContentResolver.SCHEME_FILE && pathSegments.firstOrNull() == "android_asset"
  return if (isAssetUri) AssetPath(pathSegments.drop(1).joinToString("/")) else null
}
