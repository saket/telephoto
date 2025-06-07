@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.round
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.flick.FlickToDismissState.GestureState
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Resetting
import me.saket.telephoto.flick.internal.verticalDragThenDraggable2D
import me.saket.telephoto.zoomable.internal.HapticEffect
import me.saket.telephoto.zoomable.internal.rememberHapticFeedbackPerformer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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
//  val haptic = LocalHapticFeedback.current
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

  val haptic = rememberHapticFeedbackPerformer()
  LaunchedEffect(state) {
    snapshotFlow { state.gestureState }
      .zipWithPrevious(::Pair)
      .flatMapLatest { (previous, current) ->
        val soft = current is GestureState.Dragging && !current.willDismissOnRelease

        // todo: if the content is flicked fast, there is no haptic.
        val medium1 = current is GestureState.Dragging && current.willDismissOnRelease &&
          (previous !is GestureState.Dragging || !previous.willDismissOnRelease)

        val medium2 = current is GestureState.Dragging && !current.willDismissOnRelease &&
          previous is GestureState.Dragging && previous.willDismissOnRelease

        val medium = if (medium1 || medium2) {
          flowOf(HapticEffect.GestureThresholdCrossed)
        } else {
          flowOf(HapticEffect.None)
        }

        medium
      }
      .collectLatest {
        haptic.performHapticFeedback(it)
      }
  }
}

/** Applies a spring-based easing to changes in drag offsets for smoother, more natural motion. */
@Composable
private fun FlickToDismissState.smoothOffset(): State<Offset> {
  val isRubberBanding = when (val it = gestureState) {
    is GestureState.Dragging -> !it.willDismissOnRelease
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
  // Mutex locking isn't needed for UI, which is single threaded.
  var previousValue: T? = null
  collect { currentValue ->
    previousValue?.let { previousValue ->
      emit(mapper(previousValue, currentValue))
    }
    previousValue = currentValue
  }
}
