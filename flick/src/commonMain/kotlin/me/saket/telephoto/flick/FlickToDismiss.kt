package me.saket.telephoto.flick

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch
import me.saket.telephoto.ExperimentalTelephotoApi
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
  val haptic = LocalHapticFeedback.current
  check(state is RealFlickToDismissState)

  Box(
    modifier = modifier
      .offset { state.offset.round() }
      .graphicsLayer { rotationZ = state.rotationZ }
      .verticalDragThenDraggable2D(
        enabled = true,
        state = state.draggableState,
        startDragImmediately = { state.gestureState is Resetting },
        onDragStarted = { offset ->
          state.handleOnDragStarted(offset)
        },
        onDragStopped = { velocity ->
          scope.launch {
            if (state.willDismissOnRelease(velocity.y)) {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
}
