package me.saket.telephoto.zoomable

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomableImage.ResolvedImage.GenericImage
import me.saket.telephoto.zoomable.ZoomableImage.ResolvedImage.RequiresSubSampling

/**
 * An image composable that can consume pan & zoom transformations provided by [ZoomableViewport].
 * For images that are possibly large enough to not fit in memory, sub-sampling is automatically enabled
 * so that they're displayed without any loss of detail when fully zoomed in.
 *
 * ```kotlin
 * val viewportState = rememberZoomableViewportState()
 * ZoomableViewport(viewportState) {
 *   Image(
 *     zoomableImage = …,
 *     viewportState = viewportState,
 *     contentDescription = …
 *   )
 * }
 * ```
 *
 * If sub-sampling is always desired, you could use [SubSamplingImage] directly.
 */
@Composable
fun Image(
  zoomableImage: ZoomableImage,
  viewportState: ZoomableViewportState,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
) {
  val resolved = key(zoomableImage) {
    zoomableImage.resolve()
  }
  when (resolved) {
    is GenericImage -> {
      LaunchedEffect(resolved.painter.intrinsicSize) {
        viewportState.setContentLocation(
          ZoomableContentLocation.fitInsideAndCenterAligned(resolved.painter.intrinsicSize)
        )
      }
      Image(
        modifier = modifier.applyTransformation(viewportState.contentTransformation),
        painter = resolved.painter,
        contentDescription = contentDescription,
        alignment = Alignment.Center,
        contentScale = ContentScale.Inside,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }

    is RequiresSubSampling -> {
      SubSamplingImage(
        modifier = modifier,
        state = rememberSubSamplingImageState(
          imageSource = resolved.source,
          viewportState = viewportState
        ),
        contentDescription = contentDescription,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }
  }
}

/**
 * A zoomable image that can be displayed using [me.saket.telephoto.zoomable.Image].
 *
 * If you're using Coil for loading images, Telephoto provides a default implementation
 * through `me.saket.telephoto:zoomable-image-coil`:
 *
 * ```kotlin
 * val image = ZoomableImage.coil(rememberAsyncImagePainter("https://dog.ceo"))
 * ```
 */
interface ZoomableImage {
  companion object; // For extensions.

  @Composable
  fun resolve(): ResolvedImage

  sealed interface ResolvedImage {
    /** Images that aren't bitmaps (for e.g., GIFs) and should be rendered without sub-sampling. */
    @JvmInline
    @Immutable
    value class GenericImage(val painter: Painter) : ResolvedImage

    /** Full resolution bitmaps that should be rendered using [SubSamplingImage]. */
    @Immutable
    data class RequiresSubSampling(val source: ImageSource) : ResolvedImage
  }
}
