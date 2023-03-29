package me.saket.telephoto.zoomable.coil

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImagePainter
import coil.decode.DataSource
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.accompanist.drawablepainter.DrawablePainter
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.ZoomableImage.ResolvedImage
import me.saket.telephoto.zoomable.ZoomableImage.ResolvedImage.GenericImage
import me.saket.telephoto.zoomable.ZoomableImage.ResolvedImage.RequiresSubSampling
import coil.size.Size.Companion as CoilSize

/**
 * A zoomable image that can be loaded by Coil and displayed using [me.saket.telephoto.zoomable.Image].
 *
 * ```kotlin
 * ZoomableViewport(…) {
 *   Image(
 *     zoomableImage = ZoomableImage.coil(rememberAsyncImagePainter("https://dog.ceo")),
 *     viewportState = …,
 *     contentDescription = …
 *   )
 * }
 * ```
 */
@Stable
fun ZoomableImage.Companion.coil(painter: AsyncImagePainter): ZoomableImage {
  return CoilImageResolver(painter.request, painter.imageLoader)
}

/**
 * A zoomable image that can be loaded by Coil and displayed using [me.saket.telephoto.zoomable.Image].
 *
 * ```kotlin
 * ZoomableViewport(…) {
 *   Image(
 *     zoomableImage = ZoomableImage.coil(
 *       ImageRequest.Builder(context)
 *         .data("https://dog.ceo")
 *         .build()
 *     ),
 *     viewportState = …,
 *     contentDescription = …
 *   )
 * }
 * ```
 */
@Stable
fun ZoomableImage.Companion.coil(
  request: ImageRequest,
  imageLoader: ImageLoader = request.context.imageLoader,
): ZoomableImage {
  return CoilImageResolver(request, imageLoader)
}

@Immutable
private data class CoilImageResolver(
  private val request: ImageRequest,
  private val imageLoader: ImageLoader,
) : ZoomableImage {

  @Composable
  @OptIn(ExperimentalCoilApi::class)
  override fun resolve(): ResolvedImage {
    var resolved: ResolvedImage by remember {
      mutableStateOf(GenericImage(request.placeholder.asPainter()))
    }

    LaunchedEffect(this) {
      val result = imageLoader.execute(
        request.newBuilder()
          // Prevent coil from spending any extra effort in downsizing images.
          // For bitmaps, the result will be discarded anyway in favor of their raw files.
          // For animated images, we still want them in full quality so that they can be zoomed.
          .size(CoilSize.ORIGINAL)
          // There's no easy way to be certain whether an image will require sub-sampling in
          // advance so assume it'll be needed and that the image will be read from the disk.
          .diskCachePolicy(
            when (request.diskCachePolicy) {
              CachePolicy.ENABLED -> CachePolicy.ENABLED
              CachePolicy.READ_ONLY -> CachePolicy.ENABLED
              CachePolicy.WRITE_ONLY,
              CachePolicy.DISABLED -> CachePolicy.WRITE_ONLY
            }
          )
          // This will unfortunately replace any existing target, but it is also the only
          // way to read placeholder images set using ImageRequest#placeholderMemoryCacheKey.
          // Placeholder images should be small in size so sub-sampling isn't needed here.
          .target(
            onStart = { placeholder ->
              resolved = GenericImage(placeholder.asPainter())
            }
          )
          .build()
      )

      val requestData = result.request.data
      resolved = if (result is SuccessResult && result.drawable is BitmapDrawable) {
        // Prefer reading of images directly from files whenever possible because
        // that is significantly faster than reading from their input streams.
        if (result.diskCacheKey != null) {
          val diskCache = imageLoader.diskCache!!
          val cached = diskCache[result.diskCacheKey!!] ?: error("Coil returned a null image from disk cache")
          RequiresSubSampling(ImageSource.file(cached.data))  // todo: use request.bitmapConfig?

        } else if (result.dataSource == DataSource.DISK && requestData is Uri) {
          // Image is present somewhere on the disk, but not in coil's
          // disk cache. Possibly an asset or an image shared by another app?
          RequiresSubSampling(ImageSource.contentUri(requestData))
        } else {
          GenericImage(result.drawable.asPainter())
        }
      } else {
        GenericImage(result.drawable.asPainter())
      }
    }

    return resolved
  }

  private fun Drawable?.asPainter(): Painter {
    return if (this == null) EmptyPainter else DrawablePainter(mutate())
  }

  private object EmptyPainter : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified
    override fun DrawScope.onDraw() = Unit
  }
}
