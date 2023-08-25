package me.saket.telephoto.flick

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Resetting

/**
 * A layout composable that can be flick dismissed using vertical swipe gestures.
 */
@Composable
fun FlickToDismiss(
  state: FlickToDismissState,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val haptic = LocalHapticFeedback.current
  check(state is RealFlickToDismissState)

  Box(
    modifier = modifier
      .offset { IntOffset(x = 0, y = state.offset.toInt()) }
      .graphicsLayer { rotationZ = state.rotationZ }
      .draggable(
        state = state.draggableState,
        orientation = Orientation.Vertical,
        startDragImmediately = state.gestureState is Resetting,
        onDragStarted = { offset ->
          state.handleOnDragStarted(offset)
        },
        onDragStopped = { velocity ->
          scope.launch {
            if (state.willDismissOnRelease(velocity)) {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              state.animateDismissal(velocity)
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
