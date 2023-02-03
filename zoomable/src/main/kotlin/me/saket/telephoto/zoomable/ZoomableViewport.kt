package me.saket.telephoto.zoomable

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.launch

/**
 * @param clipToBounds Defaults to true to act as a reminder that this layout should fill all available
 * space. Otherwise, gestures made outside the viewport's (unscaled) bounds will not be registered.
 */
@Composable
fun ZoomableViewport(
  state: ZoomableState,
  modifier: Modifier = Modifier,
  clipToBounds: Boolean = true,
  content: @Composable BoxScope.() -> Unit
) {
  val zoomableModifier = if (state.isReadyToInteract) {
    val scope = rememberCoroutineScope()
    Modifier
      .pointerInput(Unit) {
        detectTransformGestures(onGesture = state::onGesture)
      }
      .onAllPointersUp {
        // Reset is performed in a new coroutine. The animation will be canceled
        // if TransformableState#transform() is called again by Modifier#transformable(). todo: this doc is outdated now.
        scope.launch {
          state.animateResetOfTransformations()
        }
      }
  } else {
    Modifier
  }

  Box(
    modifier
      .let { if (clipToBounds) it.clipToBounds() else it }
      .onSizeChanged { state.viewportBounds = Rect(Offset.Zero, size = it.toSize()) }
      .then(zoomableModifier)
  ) {
    Box(
      modifier = Modifier.onGloballyPositioned { state.contentBounds = it.boundsInParent() },
      content = content
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
private suspend fun AwaitPointerEventScope.awaitAllPointersUp() {
  val allPointersDown = currentEvent.changes.fastAny { it.pressed }
  if (allPointersDown) {
    do {
      val events = awaitPointerEvent(PointerEventPass.Final)
    } while (events.changes.fastAny { it.pressed })
  }
}
