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
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.source
import java.io.FileInputStream
import java.io.InputStream
import kotlin.LazyThreadSafetyMode.NONE

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
     * Same as [SubSamplingImageSource.contentUriOrNull], but throws an error if
     * the `uri` is unsupported by [ContentResolver.openInputStream].
     */
    @Stable
    fun contentUri(
      uri: Uri,
      preview: ImageBitmap? = null
    ): SubSamplingImageSource {
      return contentUriOrNull(uri, preview)
        ?: error("Uri unsupported by ContentResolver#openInputStream(): $uri")
    }

    /**
     * An image exposed by a content provider. A common use-case for this
     * would be to display images shared by other apps.
     *
     * @param preview See [SubSamplingImageSource.preview].
     *
     * @return A `SubSamplingImageSource` if the `uri` is supported by
     * [ContentResolver.openInputStream] or null. The returned value is stable
     * and does not need to be remembered.
     */
    @Stable
    fun contentUriOrNull(
      uri: Uri,
      preview: ImageBitmap? = null
    ): SubSamplingImageSource? {
      // While ContentResolver can be used for reading assets, files, resources uris,
      // reading them through their specialized APIs can be significantly faster.
      return when (val type = UriType.parse(uri)) {
        is UriType.AssetUri -> AssetImageSource(type.asset, preview)
        is UriType.FileUri -> FileImageSource(type.path, preview, onClose = null)
        is UriType.ResourceUri -> ResourceImageSource(type.resourceId, preview)
        is UriType.ContentUri -> UriImageSource(type.uri, preview)
        null -> null
      }
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

  /** Peeks into the source without consuming its bytes. */
  fun peek(context: Context): BufferedSource

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

  override fun peek(context: Context): BufferedSource {
    return FileSystem.SYSTEM.source(path).buffer()
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

  override fun peek(context: Context): BufferedSource {
    return inputStream(context).source().buffer()
  }

  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return inputStream(context).use { stream ->
      check (stream is AssetManager.AssetInputStream) {
        error("BitmapRegionDecoder won't be able to optimize reading of this asset")
      }
      @Suppress("DEPRECATION")
      BitmapRegionDecoder.newInstance(stream, /* ignored */ false)!!
    }
  }

  private fun inputStream(context: Context): InputStream {
    return context.assets.open(asset.path, AssetManager.ACCESS_RANDOM)
  }
}

@Immutable
internal data class ResourceImageSource(
  @DrawableRes val id: Int,
  override val preview: ImageBitmap?,
) : SubSamplingImageSource {

  override fun peek(context: Context): BufferedSource {
    return inputStream(context).source().buffer()
  }

  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return inputStream(context).use { stream ->
      @Suppress("DEPRECATION")
      BitmapRegionDecoder.newInstance(stream, /* ignored */ false)!!
    }
  }

  @SuppressLint("ResourceType")
  private fun inputStream(context: Context): InputStream {
    return context.resources.openRawResource(id)
  }
}

@Immutable
internal data class UriImageSource(
  private val uri: Uri,
  override val preview: ImageBitmap?
) : SubSamplingImageSource {

  override fun peek(context: Context): BufferedSource {
    return inputStream(context).source().buffer()
  }

  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return inputStream(context).use { stream ->
      @Suppress("DEPRECATION")
      BitmapRegionDecoder.newInstance(stream, /* ignored */ false)!!
    }
  }

  private fun inputStream(context: Context): InputStream {
    return context.contentResolver.openInputStream(uri) ?: error("Failed to read uri: $uri")
  }
}

@Immutable
internal data class RawImageSource(
  private val source: () -> Source,
  override val preview: ImageBitmap? = null,
  private val onClose: Closeable?
) : SubSamplingImageSource {

  private val bufferedSource: BufferedSource by lazy(NONE) {
    source().buffer()
  }

  override fun peek(context: Context): BufferedSource {
    return bufferedSource.peek()
  }

  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return bufferedSource.inputStream().use { stream ->
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

private sealed interface UriType {
  data class AssetUri(val asset: AssetPath) : UriType
  data class FileUri(val path: Path) : UriType
  data class ResourceUri(@DrawableRes val resourceId: Int) : UriType
  data class ContentUri(val uri: Uri) : UriType

  companion object {
    fun parse(uri: Uri): UriType? {
      return when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> {
          ContentUri(uri)
        }
        ContentResolver.SCHEME_ANDROID_RESOURCE -> {
          uri.findResourceId()?.let(::ResourceUri) ?: ContentUri(uri)
        }
        ContentResolver.SCHEME_FILE -> {
          when (uri.pathSegments.firstOrNull()) {
            "android_asset" -> AssetUri(AssetPath(uri.pathSegments.drop(1).joinToString("/")))
            else -> uri.path?.let { FileUri(it.toPath()) }
          }
        }
        null -> {
          if (uri.path?.startsWith('/') == true && uri.pathSegments.isNotEmpty()) {
            // File URIs without a scheme are invalid but have had historic support
            // from many image loaders, including Coil. Telephoto is forced to support
            // them because it promises to be a drop-in replacement for AsyncImage().
            // https://github.com/saket/telephoto/issues/19
            FileUri(uri.toString().toPath())
          } else {
            null
          }
        }
        else -> null
      }
    }

    @DrawableRes private fun Uri.findResourceId(): Int? {
      check(scheme == ContentResolver.SCHEME_ANDROID_RESOURCE)
      return if (authority?.isNotBlank() == true) {
        pathSegments.singleOrNull()?.toIntOrNull()
      } else {
        null
      }
    }
  }
}
