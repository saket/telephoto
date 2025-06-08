package me.saket.telephoto.flick

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.round
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dismissed
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dismissing
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dragging
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Resetting
import me.saket.telephoto.flick.internal.verticalDragThenDraggable2D

/**
 * A layout composable that can be flick dismissed using vertical swipe gestures.
 */
@Composable
@ExperimentalTelephotoApi
fun FlickToDismiss(
  state: FlickToDismissState,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val scope = rememberCoroutineScope()
  check(state is RealFlickToDismissState)

  val offset = state.smoothOffset()
  Box(
    modifier = modifier
      .offset { offset.value.round() }
      .graphicsLayer { rotationZ = state.rotationZ }
      .verticalDragThenDraggable2D(
        enabled = true,
        state = state.draggableState,
        startDragImmediately = { state.gestureState is Resetting },
        onDragStarted = { state.handleOnDragStarted(it) },
        onDragStopped = { velocity ->
          scope.launch {
            if (state.willDismissOnRelease(velocity.y)) {
              state.animateDismissal(velocity.y)
            } else {
              state.animateReset()
            }
          }
        }
      )
      .onSizeChanged { size ->
        state.contentSize = size
      },
    content = { content() },
  )

  // Play haptic feedback when the content crosses its dismiss threshold.
  val haptic = LocalHapticFeedback.current
  LaunchedEffect(state) {
    snapshotFlow { state.gestureState }
      .zipWithPrevious(::Pair)
      .collect { (previous, current) ->
        val thresholdCrossed = current is Dragging && previous is Dragging &&
          (current.willDismissOnRelease != previous.willDismissOnRelease)

        // The gesture state can go directly from Dragging(willDismissOnRelease=false)
        // to Dismissing/Dismissed because StateFlows don't guarantee emission of intermediate states.
        val jumpedToDismiss = (current is Dismissing || current is Dismissed)
          && (previous is Dragging && !previous.willDismissOnRelease)

        if (thresholdCrossed || jumpedToDismiss) {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
      }
  }
}

/** Applies a spring-based easing to changes in drag offsets for smoother, more natural motion. */
@Composable
private fun FlickToDismissState.smoothOffset(): State<Offset> {
  val isRubberBanding = when (val it = gestureState) {
    is Dragging -> !it.willDismissOnRelease
    is Resetting -> true
    else -> false
  }
  return animateOffsetAsState(
    targetValue = if (isRubberBanding) offset / 2f else offset,
    animationSpec = spring(stiffness = Spring.StiffnessMedium),
  )
}

private fun <T, R> Flow<T>.zipWithPrevious(
  mapper: (previous: T, current: T) -> R,
): Flow<R> = flow {
  var previous: T? = null
  collect { current ->
    previous?.let { previous ->
      emit(mapper(previous, current))
    }
    previous = current
  }
}
