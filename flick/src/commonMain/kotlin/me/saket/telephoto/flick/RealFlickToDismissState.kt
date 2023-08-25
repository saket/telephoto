package me.saket.telephoto.flick

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import me.saket.telephoto.flick.FlickToDismissState.GestureState
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dismissed
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dismissing
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dragging
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Idle
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Resetting
import kotlin.math.abs

/**
 * @param dismissThresholdRatio Minimum distance the user's finger should move as a ratio
 * to the content's dimensions after which it can be dismissed.
 */
@Stable
class RealFlickToDismissState internal constructor(
  private val dismissThresholdRatio: Float = 0.3f,
  private val rotateOnDrag: Boolean = true,
) : FlickToDismissState {
  override var offset: Float by mutableStateOf(0f)
  override var gestureState: GestureState by mutableStateOf(Idle)

  override val rotationZ: Float by derivedStateOf {
    if (rotateOnDrag) {
      offsetFraction * if (dragStartedOnLeftSide) -20f else 20f
    } else {
      0f
    }
  }

  override val offsetFraction: Float by derivedStateOf {
    val contentHeight = contentSize.height
    if (contentHeight == 0) 0f else offset / contentHeight.toFloat()
  }

  internal var contentSize: IntSize by mutableStateOf(IntSize.Zero)
  private var dragStartedOnLeftSide: Boolean by mutableStateOf(false)

  internal val draggableState = DraggableState { dy ->
    offset += dy

    gestureState = when {
      gestureState is Dismissed -> gestureState
      gestureState is Resetting -> gestureState
      abs(offset) < ZoomDeltaEpsilon -> Idle
      else -> Dragging(willDismissOnRelease = abs(offsetFraction) > dismissThresholdRatio)
    }
  }

  internal fun handleOnDragStarted(startedAt: Offset) {
    dragStartedOnLeftSide = startedAt.x < (contentSize.width / 2f)
  }

  internal fun willDismissOnRelease(velocity: Float): Boolean {
    return when (val state = gestureState) {
      !is Dragging -> false
      else -> state.willDismissOnRelease || abs(velocity) >= contentSize.height * dismissThresholdRatio
    }
  }

  internal suspend fun animateDismissal(velocity: Float) {
    draggableState.drag(MutatePriority.PreventUserInput) {
      gestureState = Dismissing
      try {
        animate(
          initialValue = offset,
          targetValue = contentSize.height * if (offset > 0f) 1f else -1f,
          initialVelocity = velocity,
          animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        ) { value, _ ->
          dragBy(value - offset)
        }
      } finally {
        gestureState = Dismissed
      }
    }
  }

  internal suspend fun animateReset() {
    try {
      gestureState = Resetting
      draggableState.drag {
        Animatable(offset).animateTo(targetValue = 0f) {
          dragBy(value - offset)
        }
      }
    } finally {
      gestureState = Idle
    }
  }

  companion object {
    /** Differences below this value are ignored when comparing two zoom values. */
    private const val ZoomDeltaEpsilon = 0.01f
  }
}
