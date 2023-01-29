package me.saket.telephoto.zoomable

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.launch

/**
 * @param clipToBounds Defaults to true to act as a reminder that this layout should fill all available
 * space. Otherwise, gestures made outside the content's (unscaled) bounds will not be registered.
 */
@Composable
fun ZoomableBox(
  state: ZoomableState,
  modifier: Modifier = Modifier,
  clipToBounds: Boolean = true,
  content: @Composable () -> Unit
) {
  val zoomableModifier = if (state.isReadyToInteract) {
    val scope = rememberCoroutineScope()
    Modifier
      .let { if (clipToBounds) it.clipToBounds() else it }
      .transformable(state.transformableState)
      .onAllPointersUp {
        // Reset is performed on an independent scope, but the animation will be
        // canceled if TransformableState#transform() is called from anywhere else.
        scope.launch {
          state.animateResetOfTransformations()
        }
      }
  } else {
    Modifier
  }

  Box(modifier.then(zoomableModifier)) {
    Box(
      modifier = Modifier.onSizeChanged { state.contentLayoutSize = it },
      content = { content() }
    )
  }
}

private fun Modifier.onAllPointersUp(block: () -> Unit): Modifier {
  return pointerInput(Unit) {
    awaitEachGesture {
      awaitFirstDown(requireUnconsumed = false)
      awaitAllPointersUp()
      block()
    }
  }
}

/** Waits for all pointers to be up before returning. */
internal suspend fun AwaitPointerEventScope.awaitAllPointersUp() {
  val allPointersDown = currentEvent.changes.fastAny { it.pressed }
  if (allPointersDown) {
    do {
      val events = awaitPointerEvent(PointerEventPass.Final)
    } while (events.changes.fastAny { it.pressed })
  }
}
