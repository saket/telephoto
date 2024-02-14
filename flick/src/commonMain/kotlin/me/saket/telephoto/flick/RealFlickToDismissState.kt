package me.saket.telephoto.flick

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import me.saket.telephoto.flick.internal.animateWithDuration
import java.lang.Math.toRadians
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

@Stable
internal class RealFlickToDismissState(
  internal val dismissThresholdRatio: Float = 0.3f,
  private val rotateOnDrag: Boolean = true,
) : FlickToDismissState {
  override var offset: Float by mutableStateOf(0f)
  override var gestureState: GestureState by mutableStateOf(Idle)

  override val rotationZ: Float by derivedStateOf {
    if (rotateOnDrag) {
      offsetFraction * if (dragStartedOnLeftSide) -MaxRotation else MaxRotation
    } else {
      0f
    }
  }

  override val offsetFraction: Float by derivedStateOf {
    val contentHeight = contentSize.height
    if (contentHeight == 0) {
      0f
    } else {
      (abs(offset) / contentHeight).coerceIn(0f, 1f)
    }
  }

  internal var contentSize: IntSize by mutableStateOf(IntSize.Zero)
  private var dragStartedOnLeftSide: Boolean by mutableStateOf(false)

  internal val draggableState = DraggableState { dy ->
    offset += dy

    gestureState = when (gestureState) {
      is Idle, is Dragging -> {
        if (abs(offset) < ZoomDeltaEpsilon) {
          Idle
        } else {
          Dragging(willDismissOnRelease = abs(offsetFraction) > dismissThresholdRatio)
        }
      }
      is Resetting, is Dismissing -> gestureState
      is Dismissed -> error("drags shouldn't be received after the content is dismissed")
    }
  }

  internal fun handleOnDragStarted(startedAt: Offset) {
    dragStartedOnLeftSide = startedAt.x < (contentSize.width / 2f)
  }

  internal fun willDismissOnRelease(velocity: Float): Boolean {
    return when (val state = gestureState) {
      !is Dragging -> false
      else -> {
        // Calculate a velocity threshold that excludes short flings.
        val thresholdVelocity = 10f * contentSize.height * dismissThresholdRatio
        (state.willDismissOnRelease || abs(velocity) >= thresholdVelocity)
      }
    }
  }

  internal suspend fun animateDismissal(velocity: Float) {
    draggableState.drag(MutatePriority.PreventUserInput) {
      try {
        val distanceCoveredByRotation = if (rotateOnDrag) {
          val theta = toRadians(MaxRotation.toDouble()).toFloat()
          (1f - sin(theta)) * (theta * (contentSize.diagonal / 2))
        } else {
          0f
        }
        animateWithDuration(
          initialValue = offset,
          targetValue = (contentSize.height + distanceCoveredByRotation) * if (offset > 0f) 1f else -1f,
          initialVelocity = velocity,
          animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
          onStart = { duration ->
            gestureState = Dismissing(duration)
          }
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

  @Suppress("ConstPropertyName")
  companion object {
    /** Differences below this value are ignored when comparing two zoom values. */
    private const val ZoomDeltaEpsilon = 0.01f

    private const val MaxRotation = 20f
  }
}

private val IntSize.diagonal: Float
  get() = sqrt((width * width + height * height).toFloat())
