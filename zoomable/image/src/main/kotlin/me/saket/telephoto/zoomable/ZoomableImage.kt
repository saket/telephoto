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
 * An image composable that handles zoom & pan gestures using [Modifier.zoomable].
 * For images that are large enough to not fit in memory, sub-sampling is automatically enabled
 * so that they're displayed without any loss of detail when fully zoomed in.
 *
 * If sub-sampling is always desired, you could also use [SubSamplingImage] directly.
 */
@Composable
fun ZoomableImage(
  image: ZoomableImage,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  state: ZoomableViewportState = rememberZoomableViewportState(),
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
) {
  state.let {
    it.contentAlignment = alignment
    it.contentScale = contentScale
  }

  val resolved = key(image) {
    image.resolve()
  }
  when (resolved) {
    is GenericImage -> {
      LaunchedEffect(resolved.painter.intrinsicSize) {
        state.setContentLocation(
          ZoomableContentLocation.scaledInsideAndCenterAligned(resolved.painter.intrinsicSize)
        )
      }
      Image(
        modifier = modifier
          .zoomable(state)
          .applyTransformation(state.contentTransformation),
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
        modifier = modifier.zoomable(state),
        state = rememberSubSamplingImageState(
          imageSource = resolved.source,
          viewportState = state
        ),
        contentDescription = contentDescription,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }
  }
}

/**
 * A zoomable image that can be displayed using [me.saket.telephoto.zoomable.ZoomableImage].
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
