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
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.internal.rotateBy
import me.saket.telephoto.zoomable.internal.topLeftCoercedInside
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** todo: doc */
@Composable
fun rememberZoomableViewportState(
  maxZoomFactor: Float = 1f,
  rotationEnabled: Boolean = false,
): ZoomableViewportState {
  return remember { ZoomableViewportState() }.apply {
    this.rotationEnabled = rotationEnabled
    this.maxZoomFactor = maxZoomFactor
  }
}

@Stable
class ZoomableViewportState internal constructor() {
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
  private lateinit var contentAlignment: Alignment

  internal var maxZoomFactor: Float = 1f
  internal var rotationEnabled: Boolean = false

  /**
   * Raw size of the image/video/anything without any scaling applied.
   * Used only for determining whether the content can zoom any further.
   */
  // TODO: verify doc.
  private var unscaledContentSize by mutableStateOf(Size.Unspecified)

  /**
   * Bounds of [ZoomableViewport]'s content composable in the layout hierarchy, without any scaling applied.
   */
  // todo: should this be an IntRect?
  internal var contentLayoutBounds by mutableStateOf(Rect.Zero)

  // todo: should this be an IntRect?
  internal var viewportBounds by mutableStateOf(Rect.Zero)

  /** todo: doc. */
  internal val isReadyToInteract: Boolean by derivedStateOf {
    unscaledContentSize.isSpecified
      && contentLayoutBounds != Rect.Zero
      && viewportBounds != Rect.Zero
  }

  @Suppress("NAME_SHADOWING")
  internal fun onGesture(centroid: Offset, panDelta: Offset, zoomDelta: Float, rotationDelta: Float) {
    cancelResetAnimation?.invoke()

    // This line assumes that ZoomableViewport's content is also using the
    // same scale for resizing itself within viewport bounds. I think this is
    // okay for now because it doesn't make sense to support, say, CenterCrop.
    val scaledToFitContentSize = unscaledContentSize * ContentScale.Fit.computeScaleFactor(
      srcSize = unscaledContentSize,
      dstSize = contentLayoutBounds.size,
    )

    val rotationDelta = if (rotationEnabled) rotationDelta else 0f
    val isZoomingOut = zoomDelta < 1f
    val isZoomingIn = zoomDelta > 1f
    val isFullyZoomedOut = gestureTransformations.zoom <= 1f
    val isFullyZoomedIn = //gestureTransformations.zoom >= maxZoomFactor  // todo: this is the correct way!
      (gestureTransformations.zoom * scaledToFitContentSize.width).roundToInt() >= (unscaledContentSize.width * maxZoomFactor)

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
          val newContentBounds = Rect(offset = it, scaledToFitContentSize * newZoom)
          newContentBounds.topLeftCoercedInside(viewportBounds, contentAlignment)
        },
        zoom = newZoom,
        rotationZ = old.rotationZ + rotationDelta,
      )
    }
  }

  internal fun setContentAlignment(alignment: Alignment) {
    contentAlignment = alignment

    // Run an empty gesture to update the
    // content's position with its new alignment.
    onGesture(
      centroid = Offset.Zero,
      panDelta = Offset.Zero,
      zoomDelta = 0f,
      rotationDelta = 0f,
    )
  }

  /** todo: doc */
  fun setUnscaledContentSize(size: IntSize?) {
    setUnscaledContentSize(size?.toSize())
  }

  /** todo: doc */
  fun setUnscaledContentSize(size: Size?) {
    // todo: reset transformations when a new size is received, probably because the image was changed?
    unscaledContentSize = size ?: Size.Zero
  }

  internal suspend fun animateResetOfTransformations() {
    val minLayoutZoom = 1f
    val maxLayoutZoom = (maxZoomFactor * unscaledContentSize.width) / contentLayoutBounds.width

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

private operator fun Size.times(scale: ScaleFactor): Size {
  return Size(
    width = width * scale.scaleX,
    height = height * scale.scaleY,
  )
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
