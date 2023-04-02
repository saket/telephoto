package me.saket.telephoto.zoomable

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState

/**
 * An image composable that handles zoom & pan gestures using [Modifier.zoomable].
 * For images that are large enough to not fit in memory, sub-sampling is automatically enabled
 * so that they're displayed without any loss of detail when fully zoomed in.
 *
 * Because `Modifier.zoomable()` consumes all gestures including double-taps, [Modifier.clickable]
 * and [Modifier.combinedClickable] will not work on this composable. As an alternative, [onClick]
 * and [onLongClick] parameters can be used instead.
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
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
) {
  state.let {
    it.contentAlignment = alignment
    it.contentScale = contentScale
  }

  val commonModifiers = modifier.zoomable(
    state = state,
    onClick = onClick,
    onLongClick = onLongClick,
  )
  when (image) {
    is ZoomableImage.Generic -> {
      LaunchedEffect(image.painter.intrinsicSize) {
        state.setContentLocation(
          ZoomableContentLocation.scaledInsideAndCenterAligned(image.painter.intrinsicSize)
        )
      }
      Image(
        modifier = Modifier
          .then(commonModifiers)
          .applyTransformation(state.contentTransformation),
        painter = image.painter,
        contentDescription = contentDescription,
        alignment = Alignment.Center,
        contentScale = ContentScale.Inside,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }

    is ZoomableImage.RequiresSubSampling -> {
      SubSamplingImage(
        modifier = commonModifiers,
        state = rememberSubSamplingImageState(
          imageSource = image.source,
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
 * An image that can be displayed using `ZoomableImage()`.
 *
 * Keep in mind that this shouldn't be used directly. It is designed to provide an
 * abstraction over your favorite image loading library.
 *
 * If you're using Coil for loading images, Telephoto provides a default implementation
 * through `me.saket.telephoto:zoomable-image-coil`:
 *
 * ```kotlin
 * ZoomableAsyncImage(
 *  model = "https://example.com/image.jpg",
 *  contentDescription = …
 *)
 * ```
 */
sealed interface ZoomableImage {
  companion object; // For extensions.

  /** Images that aren't bitmaps (for e.g., GIFs) and should be rendered without sub-sampling. */
  @JvmInline
  @Immutable
  value class Generic(val painter: Painter) : ZoomableImage

  /** Full resolution bitmaps that should be rendered using [SubSamplingImage]. */
  @Immutable
  data class RequiresSubSampling(val source: ImageSource) : ZoomableImage
}
