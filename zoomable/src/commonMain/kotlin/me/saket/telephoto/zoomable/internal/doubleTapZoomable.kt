@file:Suppress("NAME_SHADOWING")

package me.saket.telephoto.zoomable.internal

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.ViewConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import me.saket.telephoto.zoomable.internal.QuickZoomEvent.GestureStopped
import me.saket.telephoto.zoomable.internal.QuickZoomEvent.Zooming
import kotlin.math.abs

/** Detects double-tap and quick zoom (double tap and hold) gestures. */
internal fun Modifier.doubleTapZoomable(
  onQuickZoomStarted: () -> Unit,
  onQuickZoomStopped: () -> Unit,
  onDoubleTap: (centroid: Offset) -> Unit,
  state: TransformableState,
  enabled: Boolean,
): Modifier {
  return composed {
    val onZoomStarted by rememberUpdatedState(onQuickZoomStarted)
    val onZoomStopped by rememberUpdatedState(onQuickZoomStopped)

    val channel = remember { Channel<QuickZoomEvent>(capacity = Channel.UNLIMITED) }
    LaunchedEffect(Unit) {
      while (isActive) {
        var event: QuickZoomEvent = channel.receive()

        try {
          state.transform(MutatePriority.UserInput) {
            while (event is Zooming) {
              onZoomStarted()
              (event as? Zooming)?.let { event ->
                transformBy(
                  centroid = event.centroid,
                  zoomChange = event.zoomDelta,
                )
              }
              event = channel.receive()
            }
          }
          (event as? GestureStopped)?.let { event ->
            if (event.dragged) {
              onZoomStopped()
            } else {
              onDoubleTap(event.centroid)
            }
          }
        } catch (e: CancellationException) {
          // Ignore the cancellation and start over again.
        }
      }
    }

    return@composed pointerInput(enabled) {
      if (enabled) {
        detectQuickZoomGestures(consumer = channel::trySend)
      }
    }
  }
}

private suspend fun PointerInputScope.detectQuickZoomGestures(consumer: (QuickZoomEvent) -> Unit) {
  awaitEachGesture {
    val firstDown = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
    val firstUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
      waitForUpOrCancellation(pass = PointerEventPass.Main)
    }

    if (firstUp != null) {
      val secondDown = awaitSecondDown(firstUp = firstUp)
      if (secondDown != null && secondDown.isWithinTouchTargetSize(firstUp)) {
        // These pointer events must be consumed right away or else Modifier.detectTapGestures()
        // will fire its click listener before Modifier.doubleTapZoomable() is able to detect zooms.
        firstDown.consume()
        firstUp.consume()

        var dragged = false

        verticalDrag(secondDown.id) { drag ->
          secondDown.consume()
          dragged = true
          val dragDelta = drag.positionChange()
          val zoomDelta = 1f + (dragDelta.y * 0.004f) // Formula copied from https://github.com/usuiat/Zoomable.
          consumer(Zooming(secondDown.position, zoomDelta))

          drag.consume()
        }

        consumer(GestureStopped(dragged = dragged, centroid = secondDown.position))
      }
    }
  }
}

context(PointerInputScope)
private fun PointerInputChange.isWithinTouchTargetSize(other: PointerInputChange): Boolean {
  val allowedDistance = viewConfiguration.minimumTouchTargetSize.toSize()
  return (position - other.position).let { difference ->
    abs(difference.x) < allowedDistance.width && abs(difference.y) < allowedDistance.height
  }
}

private sealed interface QuickZoomEvent {
  data class Zooming(
    val centroid: Offset,
    val zoomDelta: Float,
  ) : QuickZoomEvent

  data class GestureStopped(
    val dragged: Boolean,
    val centroid: Offset,
  ) : QuickZoomEvent
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
