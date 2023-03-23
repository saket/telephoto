package me.saket.telephoto.subsamplingimage

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.graphics.BitmapRegionDecoder
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import okio.Path

/**
 * Image to display with [SubSamplingImage]. Can be one of:
 *
 * * [ImageSource.file]
 * * [ImageSource.asset]
 * * [ImageSource.resource]
 * * [ImageSource.contentUri]
 *
 * See [supportedMimeTypes] for supported image formats.
 * */
sealed interface ImageSource {
  companion object {
    /**
     * Images stored on the device file system.
     *
     * This can be used with image loading libraries that store cached images on disk.
     * For example, if you're using Coil, you can read downloaded images from its disk cache:
     *
     * ```kotlin
     * val imageLoader: coil.ImageLoader = …
     * val diskCache: coil.DiskCache = imageLoader.diskCache!!
     *
     * val result = imageLoader.execute(
     *   ImageRequest.Builder(context)
     *     .data(…)
     *     .memoryCachePolicy(DISABLED)
     *     .build()
     * )
     * if (result is SuccessResult) {
     *   ImageSource.file(diskCache[result.diskCacheKey!!]!!.data)
     * }
     * ```
     *
     * The returned value is stable and does not need to be remembered.
     */
    @Stable
    fun file(path: Path): ImageSource = FileImageSource(path)

    /**
     * Images stored in `src/main/assets`.
     *
     * The returned value is stable and does not need to be remembered.
     */
    @Stable
    fun asset(name: String): ImageSource = AssetImageSource(name)

    /**
     * Images stored in `src/main/res/drawable*` directories.
     *
     * The returned value is stable and does not need to be remembered.
     * */
    @Stable
    fun resource(@DrawableRes id: Int): ImageSource = ResourceImageSource(id)

    /**
     * Images exposed by content providers. A common use-case for this
     * would be to display images shared by other apps.
     *
     * The returned value is stable and does not need to be remembered.
     */
    @Stable
    fun contentUri(uri: Uri): ImageSource = UriImageSource(uri)

    /** Formats supported by [BitmapRegionDecoder]. */
    fun supportedMimeTypes(): Set<String> {
      return setOf("image/jpeg", "image/png", "image/webp", "image/heic")
    }
  }

  // todo: doc.
  suspend fun decoder(context: Context): BitmapRegionDecoder
}

@Immutable
internal data class FileImageSource(val path: Path) : ImageSource {
  init {
    check(path.isAbsolute)
  }

  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return ParcelFileDescriptor.open(path.toFile(), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
      BitmapRegionDecoder.newInstance(fd.fileDescriptor, /* ignored */ false)
    }
  }
}

@Immutable
internal data class AssetImageSource(val assetName: String) : ImageSource {
  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return context.assets.open(assetName, AssetManager.ACCESS_RANDOM).use { stream ->
      if (BuildConfig.DEBUG && stream !is AssetManager.AssetInputStream) {
        error("BitmapRegionDecoder won't be able to optimize reading of this asset")
      }
      BitmapRegionDecoder.newInstance(stream, /* ignored */ false)!!
    }
  }
}

@Immutable
internal data class ResourceImageSource(@DrawableRes val id: Int) : ImageSource {
  @SuppressLint("ResourceType")
  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return context.resources.openRawResource(id).use { stream ->
      BitmapRegionDecoder.newInstance(stream, /* ignored */ false)!!
    }
  }
}

@Immutable
internal data class UriImageSource(val uri: Uri) : ImageSource {
  override suspend fun decoder(context: Context): BitmapRegionDecoder {
    return context.contentResolver.openInputStream(uri)
      ?.use { stream -> BitmapRegionDecoder.newInstance(stream, /* ignored */ false)!! }
      ?: error("Failed to read bitmap from uri: $uri")
  }
}
