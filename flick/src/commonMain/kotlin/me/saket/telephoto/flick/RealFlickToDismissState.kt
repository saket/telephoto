package me.saket.telephoto.flick

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.Draggable2DState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import me.saket.telephoto.flick.FlickToDismissState.GestureState
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dismissed
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dismissing
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dragging
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Idle
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Resetting
import me.saket.telephoto.flick.internal.animateWithDuration
import me.saket.telephoto.flick.internal.toRadians
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

@Stable
internal class RealFlickToDismissState(
  rotateOnDrag: Boolean = true,
  internal var dismissThresholdRatio: Float = 0.2f, // Kept in sync with rememberFlickToDismissState().
) : FlickToDismissState {

  override var offset: Offset by mutableStateOf(Offset.Zero)
  override var gestureState: GestureState by mutableStateOf(Idle)
  internal var rotateOnDrag: Boolean by mutableStateOf(rotateOnDrag)

  override val rotationZ: Float by derivedStateOf {
    if (this.rotateOnDrag) {
      offsetFraction * if (dragStartedOnLeftSide) -MaxRotationInDegrees else MaxRotationInDegrees
    } else {
      0f
    }
  }

  override val offsetFraction: Float by derivedStateOf {
    val contentHeight = contentSize.height
    if (contentHeight == 0) {
      0f
    } else {
      (abs(offset.y) / contentHeight).coerceIn(0f, 1f)
    }
  }

  internal var contentSize: IntSize by mutableStateOf(IntSize.Zero)
  private var dragStartedOnLeftSide: Boolean by mutableStateOf(false)

  internal val draggableState = Draggable2DState { delta ->
    offset += delta

    gestureState = when (gestureState) {
      is Idle, is Dragging -> {
        if (abs(offset.y) < ZoomDeltaEpsilon) {
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
        val thresholdVelocity = contentSize.height * dismissThresholdRatio * FlingSlopMultiplier
        (state.willDismissOnRelease || abs(velocity) >= thresholdVelocity)
      }
    }
  }

  internal suspend fun animateDismissal(velocity: Velocity) {
    try {
      draggableState.drag(MutatePriority.PreventUserInput) {
        val distanceCoveredByRotation = if (rotateOnDrag) {
          val theta = MaxRotationInDegrees.toRadians()
          (1f - sin(theta)) * (theta * (contentSize.diagonal / 2))
        } else {
          0f
        }
        animateWithDuration(
          initialValue = offset,
          targetValue = offset.copy(
            y = (contentSize.height + distanceCoveredByRotation) * if (offset.y > 0f) 1f else -1f,
          ),
          initialVelocity = velocity,
          animationSpec = AnimationSpec,
          onStart = { duration -> gestureState = Dismissing(duration) },
        ) { value ->
          dragBy(value - offset)
        }
      }
    } finally {
      gestureState = Dismissed
    }
  }

  internal suspend fun animateReset() {
    try {
      gestureState = Resetting
      draggableState.drag {
        Animatable(offset, Offset.VectorConverter)
          .animateTo(
            targetValue = Offset.Zero,
            animationSpec = AnimationSpec,
          ) {
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

    private const val MaxRotationInDegrees = 20f

    internal const val FlingSlopMultiplier = 10f // A large enough value to exclude short flings.

    private val AnimationSpec = spring(
      // Kept in sync with ZoomableState.DefaultSettleAnimationSpec.
      stiffness = Spring.StiffnessMedium,
      // A non-null threshold is used to avoid long trailing animations at the end,
      // which helps prevent unintended horizontal swipes from being intercepted.
      visibilityThreshold = Offset.VisibilityThreshold,
    )
  }
}

private val IntSize.diagonal: Float
  get() = sqrt((width * width + height * height).toFloat())
