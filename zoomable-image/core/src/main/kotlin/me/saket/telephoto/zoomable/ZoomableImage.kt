package me.saket.telephoto.zoomable

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
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

  Box(
    modifier = modifier,
    propagateMinConstraints = true,
  ) {
    val isFullQualityImageLoaded = when (image) {
      is ZoomableImageSource.Generic -> image.image.intrinsicSize.isSpecified
      is ZoomableImageSource.RequiresSubSampling -> state.subSamplingState?.isImageLoaded ?: false
    }

    AnimatedVisibility(
      visible = image.placeholder != null && !isFullQualityImageLoaded,
      enter = fadeIn(tween(image.crossfadeDurationMs)),
      exit = fadeOut(tween(1 /* 0 does not work */, delayMillis = image.crossfadeDurationMs)),
    ) {
      Image(
        painter = animatedPainter(image.placeholder!!).withFixedSize(
          // Align with the full-quality image even if the placeholder is smaller in size.
          // This will only work when ZoomableImage is given fillMaxSize or a fixed size.
          state.zoomableState.contentTransformation.contentSize
        ),
        contentDescription = contentDescription,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }

    val zoomable = Modifier.zoomable(
      state = state.zoomableState,
      onClick = onClick,
      onLongClick = onLongClick,
    )
    when (image) {
      is ZoomableImageSource.Generic -> {
        LaunchedEffect(image.image.intrinsicSize) {
          state.zoomableState.setContentLocation(
            ZoomableContentLocation.scaledInsideAndCenterAligned(image.image.intrinsicSize)
          )
        }
        Image(
          modifier = zoomable.applyTransformation(state.zoomableState.contentTransformation),
          painter = animatedPainter(image.image),
          contentDescription = contentDescription,
          alignment = Alignment.Center,
          contentScale = ContentScale.Inside,
          alpha = alpha,
          colorFilter = colorFilter,
        )
      }

      is ZoomableImageSource.RequiresSubSampling -> {
        val subSamplingState = rememberSubSamplingImageState(
          imageSource = image.source,
          transformation = state.zoomableState.contentTransformation,
          bitmapConfig = image.bitmapConfig
        )
        DisposableEffect(subSamplingState) {
          state.subSamplingState = subSamplingState
          onDispose {
            state.subSamplingState = null
          }
        }
        LaunchedEffect(subSamplingState.imageSize) {
          state.zoomableState.setContentLocation(
            ZoomableContentLocation.unscaledAndTopStartAligned(
              subSamplingState.imageSize?.toSize() ?: image.expectedSize
            )
          )
        }
        val animatedAlpha by animateFloatAsState(
          targetValue = if (isFullQualityImageLoaded) 1f else 0f,
          animationSpec = tween(image.crossfadeDurationMs)
        )
        SubSamplingImage(
          modifier = zoomable,
          state = subSamplingState,
          contentDescription = contentDescription,
          alpha = alpha * animatedAlpha,
          colorFilter = colorFilter,
        )
      }
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
sealed interface ZoomableImageSource {
  companion object; // For extensions.

  val placeholder: Painter?
  val crossfadeDuration: Duration
  val crossfadeDurationMs: Int get() = crossfadeDuration.inWholeMilliseconds.toInt()

  /** Images that aren't bitmaps (for e.g., GIFs) and should be rendered without sub-sampling. */
  // todo: doc
  data class Generic(
    val image: Painter,
    override val placeholder: Painter?,
    override val crossfadeDuration: Duration = Duration.ZERO,
  ) : ZoomableImageSource

  // todo: doc
  data class RequiresSubSampling(
    val source: SubSamplingImageSource,
    override val placeholder: Painter?,
    override val crossfadeDuration: Duration = Duration.ZERO,
    val expectedSize: Size = Size.Unspecified,
    val bitmapConfig: Bitmap.Config = Bitmap.Config.ARGB_8888,
  ) : ZoomableImageSource
}

@Composable
private fun animatedPainter(painter: Painter): Painter {
  // remember() is necessary for animated painters that use
  // RememberObserver's APIs for starting & stopping their animation(s).
  return remember(painter) { painter }
}
