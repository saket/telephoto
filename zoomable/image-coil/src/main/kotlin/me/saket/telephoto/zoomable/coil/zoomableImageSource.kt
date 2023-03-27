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
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.accompanist.drawablepainter.DrawablePainter
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.ZoomableImage.ImageContent.BitmapContent
import me.saket.telephoto.zoomable.ZoomableImage.ImageContent.PainterContent
import coil.size.Size.Companion as CoilSize

// todo: doc
@Stable
fun ZoomableImage.Companion.painter(
  painter: Painter
): ZoomableImage {
  return if (painter is AsyncImagePainter) {
    // Treat coil's painter specially because its image may require sub-sampling.
    // Otherwise, coil will downsize the image to fit layout bounds by default.
    CoilImageRequest(painter.request, painter.imageLoader)
  } else {
    PainterImage(painter)
  }
}

@Immutable
private data class PainterImage(
  private val painter: Painter,
) : ZoomableImage {

  @Composable
  override fun content(): ZoomableImage.ImageContent {
    return remember { PainterContent(painter) }
  }
}

@Immutable
private data class CoilImageRequest(
  private val request: ImageRequest,
  private val imageLoader: ImageLoader,
) : ZoomableImage {

  @Composable
  @OptIn(ExperimentalCoilApi::class)
  override fun content(): ZoomableImage.ImageContent {
    var content: ZoomableImage.ImageContent by remember {
      mutableStateOf(PainterContent(EmptyPainter))
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
              content = PainterContent(placeholder.asPainter())
            }
          )
          .build()
      )

      val requestData = result.request.data
      content = if (result is SuccessResult && result.drawable is BitmapDrawable) {
        // Prefer reading of images directly from files whenever possible because
        // that is significantly faster than reading from their input streams.
        if (result.diskCacheKey != null) {
          val diskCache = imageLoader.diskCache!!
          val cached = diskCache[result.diskCacheKey!!] ?: error("Coil returned a null image from disk cache")
          BitmapContent(ImageSource.file(cached.data))  // todo: use request.bitmapConfig?

        } else if (result.dataSource == DataSource.DISK && requestData is Uri) {
          // Image is present somewhere on the disk, but not in coil's
          // disk cache. Possibly an asset, a file, or a resource?
          BitmapContent(ImageSource.contentUri(requestData))
        } else {
          PainterContent(result.drawable.asPainter())
        }
      } else {
        PainterContent(result.drawable.asPainter())
      }
    }

    return content
  }

  private fun Drawable?.asPainter(): Painter {
    return if (this == null) EmptyPainter else DrawablePainter(mutate())
  }

  private object EmptyPainter : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified
    override fun DrawScope.onDraw() = Unit
  }
}
