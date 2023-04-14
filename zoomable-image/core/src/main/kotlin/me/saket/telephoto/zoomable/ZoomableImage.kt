package me.saket.telephoto.zoomable

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.toSize
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import kotlin.time.Duration

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
  image: ZoomableImageSource,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
) {
  state.zoomableState.also {
    it.contentAlignment = alignment
    it.contentScale = contentScale
    it.autoApplyTransformations = false
  }

  val zoomable = modifier.zoomable(
    state = state.zoomableState,
    onClick = onClick,
    onLongClick = onLongClick,
  )

  val isSubSampledImageDisplayed by remember(state) {
    derivedStateOf { state.subSamplingState?.isImageDisplayed ?: false }
  }

  Box {
    if (image.source != null) {
      val subSamplingState = rememberSubSamplingImageState(
        imageSource = image.source,
        transformation = state.zoomableState.contentTransformation,
        bitmapConfig = image.bitmapConfig
      ).also {
        state.subSamplingState = it
      }
      LaunchedEffect(subSamplingState.imageSize) {
        state.zoomableState.setContentLocation(
          ZoomableContentLocation.unscaledAndTopStartAligned(
            subSamplingState.imageSize?.toSize() ?: image.expectedSize
          )
        )
      }
      SubSamplingImage(
        modifier = zoomable,
        state = subSamplingState,
        contentDescription = contentDescription,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }

    AnimatedVisibility(
      visible = !isSubSampledImageDisplayed,
      enter = fadeIn(tween(image.crossfadeDuration.inWholeMilliseconds.toInt())),
      exit = fadeOut(tween(image.crossfadeDuration.inWholeMilliseconds.toInt())),
    ) {
      LaunchedEffect(Unit) {
        state.subSamplingState = null
      }
      val placeholderSize = image.expectedSize.takeOrElse { image.placeholder.intrinsicSize }
      LaunchedEffect(placeholderSize) {
        state.zoomableState.setContentLocation(
          ZoomableContentLocation.unscaledAndTopStartAligned(placeholderSize)
        )
      }
      Image(
        modifier = zoomable,
        painter = zoomablePainter(
          painter = image.placeholder,
          transformation = state.zoomableState.contentTransformation
        ),
        contentDescription = contentDescription,
        alignment = Alignment.TopStart,
        contentScale = ContentScale.None,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }
  }
}

/**
 * An image that can be displayed using [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImageSource].
 *
 * Keep in mind that this shouldn't be used directly. It is designed to provide an
 * abstraction over your favorite image loading library.
 *
 * If you're using Coil for loading images, Telephoto provides a default implementation
 * through [ZoomableAsyncImage()][me.saket.telephoto.zoomable.coil.ZoomableAsyncImage]
 * (`me.saket.telephoto:zoomable-image-coil`).
 *
 * ```kotlin
 * ZoomableAsyncImage(
 *  model = "https://example.com/image.jpg",
 *  contentDescription = …
 *)
 * ```
 */
@Immutable
data class ZoomableImageSource(
  val source: SubSamplingImageSource?,
  val placeholder: Painter = EmptyPainter,
  val expectedSize: Size = Size.Unspecified,
  val bitmapConfig: Bitmap.Config = Bitmap.Config.ARGB_8888,
  val crossfadeDuration: Duration = Duration.ZERO,
) {
  companion object; // For extensions.

  /** Images that aren't bitmaps (for e.g., GIFs) and should be rendered without sub-sampling. */
  constructor(painter: Painter) : this(
    placeholder = painter,
    source = null
  )
}

private object EmptyPainter : Painter() {
  override val intrinsicSize: Size get() = Size.Unspecified
  override fun DrawScope.onDraw() = Unit
}
