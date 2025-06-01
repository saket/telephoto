package me.saket.telephoto.flick.internal

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.Draggable2DState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.saket.telephoto.flick.internal.DragEvent.DragDelta
import me.saket.telephoto.flick.internal.DragEvent.DragStarted
import me.saket.telephoto.flick.internal.DragEvent.DragStopped

/** Like [Modifier.draggable2D], but only accepts gestures started by vertical drags. */
internal fun Modifier.verticalDragThenDraggable2D(
  state: Draggable2DState,
  enabled: Boolean,
  startDragImmediately: () -> Boolean,
  onDragStarted: (startedPosition: Offset) -> Unit,
  onDragStopped: (velocity: Velocity) -> Unit,
): Modifier {
  if (!enabled) {
    return this
  }

  return this.pointerInput(state) {
    coroutineScope {
      val dragEvents = Channel<DragEvent>(capacity = Channel.UNLIMITED)
      launch(start = CoroutineStart.UNDISPATCHED) {
        while (isActive) {
          var event = dragEvents.receive()
          if (event !is DragStarted) continue
          onDragStarted(event.startPosition)
          try {
            state.drag(MutatePriority.UserInput) {
              do {
                event = dragEvents.receive()
                (event as? DragDelta)?.delta?.let { dragBy(it) }
              } while (event is DragDelta)
            }
            (event as? DragStopped)?.let {
              onDragStopped(it.velocity)
            }
          } catch (e: CancellationException) {
            onDragStopped(Velocity.Zero)
          }
        }
      }

      launch(start = CoroutineStart.UNDISPATCHED) {
        detectVerticalThenAllDrags(
          shouldAwaitTouchSlop = { !startDragImmediately() },
          onEvent = dragEvents::trySend,
        )
      }
    }
  }
}

private suspend fun PointerInputScope.detectVerticalThenAllDrags(
  shouldAwaitTouchSlop: () -> Boolean,
  onEvent: (DragEvent) -> Unit,
) {
  val velocityTracker = VelocityTracker()
  try {
    detectVerticalThenAllDrags(
      shouldAwaitTouchSlop = shouldAwaitTouchSlop,
      onDragStart = { down, slopTriggerChange, overSlopOffset ->
        velocityTracker.addPointerInputChange(down)
        onEvent(DragStarted(slopTriggerChange.position - overSlopOffset))
      },
      onDrag = { change, dragAmount ->
        velocityTracker.addPointerInputChange(change)
        onEvent(DragDelta(dragAmount))
      },
      onDragEnd = {
        val velocity = velocityTracker.calculateVelocity().toValidVelocity()
        velocityTracker.resetTracking()
        onEvent(DragStopped(velocity))
      },
      onDragCancel = {
        onEvent(DragStopped(Velocity.Zero))
      }
    )
  } catch (e: CancellationException) {
    onEvent(DragStopped(Velocity.Zero))
  }
}

/** Adapted from [detectDragGestures]. */
private suspend fun PointerInputScope.detectVerticalThenAllDrags(
  shouldAwaitTouchSlop: () -> Boolean,
  onDragStart: (down: PointerInputChange, slopTriggerChange: PointerInputChange, overSlopOffset: Offset) -> Unit,
  onDragEnd: (change: PointerInputChange) -> Unit,
  onDragCancel: () -> Unit,
  onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
  awaitEachGesture {
    val initialDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val awaitTouchSlop = shouldAwaitTouchSlop()
    if (!awaitTouchSlop) {
      initialDown.consume()
    }

    // Just like Modifier.detectDragGestures(), this modifier waits for the first down event twice --
    // once on the initial pass for immediate setup or slop skipping, and again on the main pass so
    // that children and other handlers get first dibs before the drag is locked in.
    val down = awaitFirstDown(requireUnconsumed = false)
    var drag: PointerInputChange?
    var overslop = Offset.Zero

    if (awaitTouchSlop) {
      // Note for self: this runs in a loop to find the
      // first _unconsumed_ event that exceeds the slop.
      do {
        // todo: replace this with awaitVerticalPointerSlopOrCancellation when
        //  https://issuetracker.google.com/issues/298903681 is released.
        drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
          change.consume()
          overslop = Offset(0f, over)
        }
      } while (drag != null && !drag.isConsumed)
    } else {
      drag = initialDown
    }
    if (drag == null) {
      return@awaitEachGesture
    }

    onDragStart(down, drag, overslop)
    onDrag(drag, overslop)

    var lastDragEvent: PointerInputChange? = null
    val completedNormally = drag(pointerId = drag.id) { change ->
      lastDragEvent = change
      onDrag(change, change.positionChange())
      change.consume()
    }
    if (completedNormally && lastDragEvent != null) {
      onDragEnd(lastDragEvent!!)
    } else {
      onDragCancel()
    }
  }
}

private sealed interface DragEvent {
  data class DragStarted(val startPosition: Offset) : DragEvent
  data class DragStopped(val velocity: Velocity) : DragEvent
  data class DragDelta(val delta: Offset) : DragEvent
}

// https://issuetracker.google.com/issues/309841148
private fun Velocity.toValidVelocity() =
  Velocity(if (this.x.isNaN()) 0f else this.x, if (this.y.isNaN()) 0f else this.y)
