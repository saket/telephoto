@file:Suppress("NAME_SHADOWING")

package me.saket.telephoto.zoomable.internal

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import me.saket.telephoto.zoomable.internal.QuickZoomEvent.QuickZoomStopped
import me.saket.telephoto.zoomable.internal.QuickZoomEvent.Zooming
import kotlin.math.abs

/**
 * Detects tap and quick zoom gestures.
 *
 * In a previous version, this used to only detect quick zoom gestures because taps were handled
 * separately using [detectTapGestures]. That was removed because preventing [detectTapGestures]
 * from consuming all events was proving to be messy and slightly difficult to follow.
 */
internal fun Modifier.tappableAndQuickZoomable(
  onPress: (Offset) -> Unit,
  onTap: ((Offset) -> Unit)?,
  onLongPress: ((Offset) -> Unit)?,
  onDoubleTap: (centroid: Offset) -> Unit,
  onQuickZoomStopped: () -> Unit,
  transformable: TransformableState,
  gesturesEnabled: Boolean,
): Modifier {
  return composed {
    val onPress by rememberUpdatedState(onPress)
    val onTap by rememberUpdatedState(onTap)
    val onLongPress by rememberUpdatedState(onLongPress)
    val onDoubleTap by rememberUpdatedState(onDoubleTap)
    val onQuickZoomStopped by rememberUpdatedState(onQuickZoomStopped)

    val quickZoomEvents = remember { Channel<QuickZoomEvent>(capacity = Channel.UNLIMITED) }
    LaunchedEffect(Unit) {
      while (isActive) {
        var event: QuickZoomEvent = quickZoomEvents.receive()

        try {
          transformable.transform(MutatePriority.UserInput) {
            while (event is Zooming) {
              (event as? Zooming)?.let { event ->
                transformBy(
                  centroid = event.centroid,
                  zoomChange = event.zoomDelta,
                )
              }
              event = quickZoomEvents.receive()
            }
          }
          (event as? QuickZoomStopped)?.let {
            onQuickZoomStopped()
          }
        } catch (e: CancellationException) {
          // Ignore the cancellation and start over again.
        }
      }
    }

    return@composed pointerInput(gesturesEnabled) {
      detectTapAndQuickZoomGestures(
        onPress = onPress,
        onTap = onTap,
        onLongPress = onLongPress,
        onDoubleTap = {
          if (gesturesEnabled) {
            onDoubleTap(it)
          }
        },
        onQuickZoom = {
          if (gesturesEnabled) {
            quickZoomEvents.trySend(it)
          }
        },
      )
    }
  }
}

private suspend fun PointerInputScope.detectTapAndQuickZoomGestures(
  onPress: (Offset) -> Unit,
  onTap: ((Offset) -> Unit)?,
  onLongPress: ((Offset) -> Unit)?,
  onDoubleTap: (centroid: Offset) -> Unit,
  onQuickZoom: (QuickZoomEvent) -> Unit,
) {
  awaitEachGesture {
    val firstDown = awaitFirstDown()
    firstDown.consume()
    onPress(firstDown.position)

    val longPressTimeout = onLongPress?.let { viewConfiguration.longPressTimeoutMillis } ?: (Long.MAX_VALUE / 2)

    var firstUp: PointerInputChange? = null
    try {
      // Wait for first tap up or long press.
      firstUp = withTimeout(longPressTimeout) {
        waitForUpOrCancellation()
      }
      firstUp?.consume()

    } catch (_: PointerEventTimeoutCancellationException) {
      onLongPress?.invoke(firstDown.position)
      consumeUntilUp()
    }

    if (firstUp != null) {
      val secondDown = awaitSecondDown(firstUp = firstUp)
      secondDown?.consume()

      if (secondDown == null) {
        // No valid second tap started.
        onTap?.invoke(firstUp.position)

      } else if (areWithinTouchTargetSize(firstUp, secondDown)) {
        var dragged = false
        verticalDrag(secondDown.id) { drag ->
          dragged = true
          val dragDelta = drag.positionChange()
          val zoomDelta = 1f + (dragDelta.y * 0.004f) // Formula copied from https://github.com/usuiat/Zoomable.
          onQuickZoom(Zooming(secondDown.position, zoomDelta))
          drag.consume()
        }

        if (dragged) {
          onQuickZoom(QuickZoomStopped)
        } else {
          onDoubleTap(secondDown.position)
        }
      }
    }
  }
}

private fun PointerInputScope.areWithinTouchTargetSize(
  first: PointerInputChange,
  second: PointerInputChange
): Boolean {
  val allowedDistance = viewConfiguration.minimumTouchTargetSize.toSize()
  return (second.position - first.position).let { difference ->
    abs(difference.x) < allowedDistance.width && abs(difference.y) < allowedDistance.height
  }
}

private sealed interface QuickZoomEvent {
  data class Zooming(
    val centroid: Offset,
    val zoomDelta: Float,
  ) : QuickZoomEvent

  object QuickZoomStopped : QuickZoomEvent
}

/**
 * Copied from TapGestureDetector.kt. Can be deleted once
 * [it is made public](https://issuetracker.google.com/u/issues/279780929).
 *
 * Waits for [ViewConfiguration.doubleTapTimeoutMillis] for a second press event. If a
 * second press event is received before the time out, it is returned or `null` is returned
 * if no second press is received.
 */
private suspend fun AwaitPointerEventScope.awaitSecondDown(
  firstUp: PointerInputChange
): PointerInputChange? = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
  val minUptime = firstUp.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
  var change: PointerInputChange
  // The second tap doesn't count if it happens before DoubleTapMinTime of the first tap
  do {
    change = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
  } while (change.uptimeMillis < minUptime)
  change
}

/**
 * Copied from TapGestureDetector.kt.
 *
 * Consumes all pointer events until nothing is pressed and then returns. This method assumes
 * that something is currently pressed.
 */
private suspend fun AwaitPointerEventScope.consumeUntilUp() {
  do {
    val event = awaitPointerEvent()
    event.changes.fastForEach { it.consume() }
  } while (event.changes.fastAny { it.pressed })
}
