package me.saket.telephoto.subsamplingimage

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import okio.buffer
import java.io.InputStream

sealed interface ImageSource {
  companion object {
    /**
     * A streaming source of bitmap for displaying locally stored images.
     *
     * This can be used with image loading libraries that store cached images on disk.
     * For example, if you're using Coil, you can read downloaded images from its disk cache:
     *
     * ```kotlin
     * val imageLoader: coil.ImageLoader = …
     * val result = imageLoader.execute(
     *   ImageRequest.Builder(context)
     *     .data(…)
     *     .memoryCachePolicy(DISABLED)
     *     .build()
     * )
     *
     * if (result is SuccessResult) {
     *   ImageSource.stream {
     *     val diskCache = imageLoader.diskCache!!
     *     diskCache.fileSystem.source(diskCache[result.diskCacheKey!!]!!.data)
     *   }
     * }
     * ```
     *
     * Make sure that a new copy of a stream is produced each time [producer] is called instead
     * of reusing a cached value. This is because multiple streams are used for decoding bitmaps
     * in parallel across multiple threads.
     *
     * The returned value isn't stable and should be used with [remember] if needed.
     */
    fun stream(producer: suspend (Context) -> okio.Source): ImageSource {
      return StreamingImageSource(producer)
    }

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
  }

  // todo: doc.
  suspend fun stream(context: Context): InputStream
}

@Immutable
internal data class StreamingImageSource(val producer: suspend (Context) -> okio.Source) : ImageSource {
  override suspend fun stream(context: Context) =
    producer(context).buffer().inputStream()
}

@Immutable
internal data class AssetImageSource(val assetName: String) : ImageSource {
  override suspend fun stream(context: Context) =
    context.assets.open(assetName, AssetManager.ACCESS_RANDOM)
}

@Immutable
internal data class ResourceImageSource(@DrawableRes val id: Int) : ImageSource {
  @SuppressLint("ResourceType")
  override suspend fun stream(context: Context) =
    context.resources.openRawResource(id)
}

@Immutable
internal data class UriImageSource(val uri: Uri) : ImageSource {
  @SuppressLint("Recycle")
  override suspend fun stream(context: Context) =
    context.contentResolver.openInputStream(uri) ?: error("Failed to read from uri: $uri")
}
