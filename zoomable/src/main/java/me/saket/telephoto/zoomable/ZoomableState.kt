package me.saket.telephoto.zoomable

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.animatePanBy
import androidx.compose.foundation.gestures.animateRotateBy
import androidx.compose.foundation.gestures.animateZoomBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/** todo: doc */
@Composable
fun rememberZoomableState(
  maxZoomFactor: Float = 1f,
  rotationEnabled: Boolean = false,
): ZoomableState {
  return remember { ZoomableState() }.apply {
    this.rotationEnabled = rotationEnabled
    this.maxZoomFactor = maxZoomFactor
  }
}

@Stable
class ZoomableState internal constructor() {
  /** todo: doc */
  var transformations by mutableStateOf(ZoomableContentTransformations.Empty)

  internal var maxZoomFactor: Float = 1f
  internal var rotationEnabled: Boolean = false

  /** Full size of the image/video/whatever without any scaling applied due to zoom. */
  private var unscaledContentSize: IntSize by mutableStateOf(IntSize.Zero)

  /** Size of the content composable in the layout hierarchy. */
  internal var contentLayoutSize by mutableStateOf(IntSize.Zero)

  internal val isReadyToInteract by derivedStateOf {
    unscaledContentSize != IntSize.Zero && contentLayoutSize != IntSize.Zero
  }

  internal val transformableState = TransformableState { zoomDelta, offsetDelta, rotationDelta ->
    val isZoomingOut = zoomDelta < 1f
    val isFullyZoomedOut = transformations.scale <= 1f

    val isZoomingIn = zoomDelta > 1f
    val isFullyZoomedIn =
      (transformations.scale * contentLayoutSize.width).roundToInt() >= (unscaledContentSize.width * maxZoomFactor)

    // Apply elasticity to zoom once content can't zoom any further.
    val elasticZoomDelta = when {
      isFullyZoomedIn && isZoomingIn -> 1f + zoomDelta / 250
      isFullyZoomedOut && isZoomingOut -> 1f - zoomDelta / 250
      else -> zoomDelta
    }

    transformations = transformations.let {
      it.copy(
        scale = it.scale * elasticZoomDelta,
        rotationZ = if (rotationEnabled) it.rotationZ + rotationDelta else 0f,
        offset = it.offset + offsetDelta,
        transformOrigin = TransformOrigin.Center
      )
    }
  }

  /** todo: doc */
  fun setUnscaledContentSize(size: IntSize?) {
    unscaledContentSize = size ?: IntSize.Zero
  }

  /** todo: doc */
  fun setUnscaledContentSize(size: Size?) {
    setUnscaledContentSize(size?.roundToIntSize())
  }

  /** Combines [animateRotateBy], [animateZoomBy] and [animatePanBy]. */
  internal suspend fun animateResetOfTransformations() {
    val minScale = 1f
    val maxScale = maxZoomFactor * (unscaledContentSize.width / contentLayoutSize.width.toFloat())

    val current = transformations
    val target = current.copy(
      scale = when {
        current.scale < minScale -> minScale
        current.scale > maxScale -> maxScale
        else -> current.scale
      },
      rotationZ = 0f,
      // todo: this isn't perfect. pan should only reset if
      //  it's content edges don't overlap with this layout's edges.
      offset = Offset.Zero,
    )

    transformableState.transform {
      AnimationState(initialValue = 0f).animateTo(
        targetValue = 1f,
        animationSpec = spring()
      ) {
        transformations = transformations.copy(
          scale = lerp(start = current.scale, stop = target.scale, fraction = value),
          rotationZ = lerp(start = current.rotationZ, stop = target.rotationZ, fraction = value),
          offset = lerp(start = current.offset, stop = target.offset, fraction = value)
        )
      }
    }
  }
}

private fun Size.roundToIntSize(): IntSize {
  return IntSize(width = width.roundToInt(), height = height.roundToInt())
}
