package me.saket.telephoto.zoomable

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.internal.topLeftCoercedInside
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
  /**
   * Transformations that should be applied by the viewport to its content.
   *
   * todo: doc
   */
  val contentTransformations: ZoomableContentTransformations by derivedStateOf {
    gestureTransformations.let {
      ZoomableContentTransformations(
        viewportSize = viewportBounds.size,
        scale = it.zoom,
        offset = -it.offset * it.zoom,
        rotationZ = it.rotationZ,
      )
    }
  }

  private var gestureTransformations by mutableStateOf(GestureTransformations.Empty)
  private var cancelResetAnimation: (() -> Unit)? = null

  internal var maxZoomFactor: Float = 1f
  internal var rotationEnabled: Boolean = false

  /** Raw size of the image/video/anything without any scaling applied. */
  private var unscaledContentSize by mutableStateOf(IntSize.Zero)

  /**
   * Size of the composable in the layout hierarchy that displays the content within its bounds.
   * This size should be independent of any scaling applied to the content.
   */
  // todo: should this be an IntRect?
  internal var contentBounds by mutableStateOf(Rect.Zero)

  // todo: should this be an IntRect?
  internal var viewportBounds by mutableStateOf(Rect.Zero)

  /** todo: doc. */
  internal val isReadyToInteract: Boolean by derivedStateOf {
    unscaledContentSize != IntSize.Zero
      && contentBounds != Rect.Zero
      && viewportBounds != Rect.Zero
  }

  @Suppress("NAME_SHADOWING")
  internal fun onGesture(centroid: Offset, panDelta: Offset, zoomDelta: Float, rotationDelta: Float) {
    cancelResetAnimation?.invoke()

    val rotationDelta = if (rotationEnabled) rotationDelta else 0f

    val isZoomingOut = zoomDelta < 1f
    val isFullyZoomedOut = gestureTransformations.zoom <= 1f

    val isZoomingIn = zoomDelta > 1f
    val isFullyZoomedIn =
      (gestureTransformations.zoom * contentBounds.width).roundToInt() >= (unscaledContentSize.width * maxZoomFactor)

    // Apply elasticity to zoom once content can't zoom any further.
    val zoomDelta = when {
      isFullyZoomedIn && isZoomingIn -> 1f + zoomDelta / 250
      isFullyZoomedOut && isZoomingOut -> 1f - zoomDelta / 500
      else -> zoomDelta
    }

    val oldZoom = gestureTransformations.zoom
    val newZoom = gestureTransformations.zoom * zoomDelta
    val oldOffset = gestureTransformations.offset

    // Copied from androidx samples:
    // https://github.com/androidx/androidx/blob/643b1cfdd7dfbc5ccce1ad951b6999df049678b3/compose/foundation/foundation/samples/src/main/java/androidx/compose/foundation/samples/TransformGestureSamples.kt#L87
    //
    // For natural zooming and rotating, the centroid of the gesture
    // should be the fixed point where zooming and rotating occurs.
    //
    // We compute where the centroid was (in the pre-transformed coordinate
    // space), and then compute where it will be after this delta.
    //
    // We then compute what the new offset should be to keep the centroid
    // visually stationary for rotating and zooming, and also apply the pan.
    //
    // This is comparable to performing a pre-translate + scale + post-translate on a Matrix.
    //
    // I found this maths difficult to understand, so here's another explanation in
    // Ryan Harter's words:
    //
    // The basic idea is that to rotate or scale around an arbitrary point, you
    // translate so that that point is in the center, then you rotate, then scale,
    // then move everything back.
    //
    //              Move the centroid to the center
    //                  of panned content(?)
    //                           |                                                    Scale
    //                           |                                                      |                Move back
    //                           |                                                      |           (+ new translation)
    //                           |                                                      |                    |
    //              _____________|_________________                             ________|_________   ________|_________
    val newOffset = (oldOffset + centroid / oldZoom).rotateBy(rotationDelta) - (centroid / newZoom + panDelta / oldZoom)

    gestureTransformations = gestureTransformations.let { old ->
      old.copy(
        offset = newOffset.withZoom(-newZoom) {
          val newContentBounds = Rect(offset = it, contentBounds.size * newZoom)
          newContentBounds.topLeftCoercedInside(viewportBounds)
        },
        zoom = newZoom,
        rotationZ = old.rotationZ + rotationDelta,
      )
    }
  }

  /** todo: doc */
  fun setUnscaledContentSize(size: IntSize?) {
    // todo: reset transformations when a new size is received, probably because the image was changed?
    unscaledContentSize = size ?: IntSize.Zero
  }

  /** todo: doc */
  fun setUnscaledContentSize(size: Size?) {
    setUnscaledContentSize(size?.roundToIntSize())
  }

  /**
   * Copied from [androidx samples](https://github.com/androidx/androidx/blob/643b1cfdd7dfbc5ccce1ad951b6999df049678b3/compose/foundation/foundation/samples/src/main/java/androidx/compose/foundation/samples/TransformGestureSamples.kt#L61).
   *
   * Rotates the given offset around the origin by the given angle in degrees.
   * A positive angle indicates a counterclockwise rotation around the right-handed
   * 2D Cartesian coordinate system.
   *
   * See: [Rotation matrix](https://en.wikipedia.org/wiki/Rotation_matrix)
   */
  private fun Offset.rotateBy(angle: Float): Offset {
    val angleInRadians = angle * PI / 180
    return Offset(
      (x * cos(angleInRadians) - y * sin(angleInRadians)).toFloat(),
      (x * sin(angleInRadians) + y * cos(angleInRadians)).toFloat()
    )
  }

  internal suspend fun animateResetOfTransformations() {
    val minLayoutZoom = 1f
    val maxLayoutZoom = (maxZoomFactor * unscaledContentSize.width) / contentBounds.width

    val current = gestureTransformations
    val target = GestureTransformations.Empty.copy(
      zoom = when {
        current.zoom < minLayoutZoom -> minLayoutZoom
        current.zoom > maxLayoutZoom -> maxLayoutZoom
        else -> current.zoom
      }
    )

    coroutineScope {
      val animationJob = launch {
        AnimationState(initialValue = 0f).animateTo(
          targetValue = 1f,
          animationSpec = spring()
        ) {
          gestureTransformations = gestureTransformations.copy(
            zoom = lerp(start = current.zoom, stop = target.zoom, fraction = value),
            rotationZ = lerp(start = current.rotationZ, stop = target.rotationZ, fraction = value),
            // todo: uncomment and improve to only reset out-of-bounds offset.
            //offset = androidx.compose.ui.geometry.lerp(start = current.offset, stop = target.offset, fraction = value),
          )
        }
      }
      cancelResetAnimation = { animationJob.cancel() }
      animationJob.invokeOnCompletion {
        cancelResetAnimation = null
      }
    }
  }
}

// todo: it'd be nice to replace this with a Matrix.
private data class GestureTransformations(
  val offset: Offset,
  val zoom: Float,
  val rotationZ: Float,
) {
  companion object {
    val Empty = GestureTransformations(
      offset = Offset.Zero,
      zoom = 1f,
      rotationZ = 0f,
    )
  }
}

/** This is named along the lines of `Canvas#withTranslate()`. */
private fun Offset.withZoom(zoom: Float, action: (Offset) -> Offset): Offset {
  return action(this * zoom) / zoom
}

private fun Size.roundToIntSize(): IntSize {
  return IntSize(width = width.roundToInt(), height = height.roundToInt())
}
