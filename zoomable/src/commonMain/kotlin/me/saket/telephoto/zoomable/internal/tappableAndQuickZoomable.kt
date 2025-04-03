@file:OptIn(ExperimentalTelephotoApi::class)

package me.saket.telephoto.zoomable.internal

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.CoordinateSpace
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.SpatialOffset
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.internal.QuickZoomEvent.QuickZoomStopped
import me.saket.telephoto.zoomable.internal.QuickZoomEvent.Zooming
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Detects tap and quick zoom gestures.
 *
 * In a previous version, this used to only detect quick zoom gestures because taps were handled
 * separately using [detectTapGestures]. That was removed because preventing [detectTapGestures]
 * from consuming all events was proving to be messy and slightly difficult to follow.
 */
internal data class TappableAndQuickZoomableElement(
  private val onPress: () -> Unit,
  private val onTap: ((SpatialOffset) -> Unit)?,
  private val onLongPress: ((SpatialOffset) -> Unit)?,
  private val onDoubleTap: ((centroid: SpatialOffset) -> Unit)?,
  private val onQuickZoomStopped: () -> Unit,
  private val transformableState: TransformableState,
  private val quickZoomEnabled: Boolean,
) : ModifierNodeElement<TappableAndQuickZoomableNode>() {

  override fun create(): TappableAndQuickZoomableNode {
    return TappableAndQuickZoomableNode(
      onPress = onPress,
      onTap = onTap,
      onLongPress = onLongPress,
      onDoubleTap = onDoubleTap,
      onQuickZoomStopped = onQuickZoomStopped,
      transformableState = transformableState,
      quickZoomEnabled = quickZoomEnabled,
    )
  }

  override fun update(node: TappableAndQuickZoomableNode) {
    node.update(
      onPress = onPress,
      onTap = onTap,
      onLongPress = onLongPress,
      onDoubleTap = onDoubleTap,
      onQuickZoomStopped = onQuickZoomStopped,
      transformableState = transformableState,
      quickZoomEnabled = quickZoomEnabled,
    )
  }
}

internal class TappableAndQuickZoomableNode(
  private var onPress: () -> Unit,
  private var onTap: ((SpatialOffset) -> Unit)?,
  private var onLongPress: ((SpatialOffset) -> Unit)?,
  private var onDoubleTap: ((centroid: SpatialOffset) -> Unit)?,
  private var onQuickZoomStopped: () -> Unit,
  private var transformableState: TransformableState,
  private var quickZoomEnabled: Boolean,
) : DelegatingNode() {

  private val quickZoomEvents = Channel<QuickZoomEvent>(capacity = Channel.UNLIMITED)

  private val pointerInputNode = delegate(SuspendingPointerInputModifierNode {
    coroutineScope {
      launch(start = CoroutineStart.UNDISPATCHED) {
        while (isActive) {
          var event: QuickZoomEvent = quickZoomEvents.receive()
          try {
            transformableState.transform(MutatePriority.UserInput) {
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

      detectTapAndQuickZoomGestures(
        // Note to self: these lambdas should not pass a reference
        // to their delegated lambdas because they can change.
        onPress = {
          onPress()
        },
        onTap = if (onTap != null) {
          { offset -> onTap?.invoke(SpatialOffset(offset, CoordinateSpace.Viewport)) }
        } else null,
        onLongPress = if (onLongPress != null) {
          { offset -> onLongPress?.invoke(SpatialOffset(offset, CoordinateSpace.Viewport)) }
        } else null,
        onDoubleTap = if (onDoubleTap != null) {
          { centroid -> onDoubleTap?.invoke(SpatialOffset(centroid, CoordinateSpace.Viewport)) }
        } else null,
        onQuickZoom = if (quickZoomEnabled) {
          { event -> quickZoomEvents.trySend(event) }
        } else null,
      )
    }
  })

  fun update(
    onPress: () -> Unit,
    onTap: ((SpatialOffset) -> Unit)?,
    onLongPress: ((SpatialOffset) -> Unit)?,
    onDoubleTap: ((centroid: SpatialOffset) -> Unit)?,
    onQuickZoomStopped: () -> Unit,
    transformableState: TransformableState,
    quickZoomEnabled: Boolean,
  ) {
    // This node should be reset if:
    // - Nullable args to detectTapAndQuickZoomGestures() go from not-defined to
    //   defined and vice versa, as their nullability is captured by the args.
    // - The entire gesture state is changed.
    val needsReset = (this.onTap == null) != (onTap == null) ||
      (this.onLongPress == null) != (onLongPress == null) ||
      (this.onDoubleTap == null) != (onDoubleTap == null) ||
      (this.quickZoomEnabled != quickZoomEnabled) ||
      (this.transformableState != transformableState)

    // These are captured as references inside callbacks to detectTapAndQuickZoomGestures,
    // so there's no need to reset pointer input handling.
    this.onPress = onPress
    this.onDoubleTap = onDoubleTap
    this.quickZoomEnabled = quickZoomEnabled
    this.onQuickZoomStopped = onQuickZoomStopped

    if (needsReset) {
      this.onTap = onTap
      this.onLongPress = onLongPress
      this.transformableState = transformableState
      pointerInputNode.resetPointerInputHandler()
    }
  }
}

private suspend fun PointerInputScope.detectTapAndQuickZoomGestures(
  onPress: (Offset) -> Unit,
  onTap: ((Offset) -> Unit)?,
  onLongPress: ((Offset) -> Unit)?,
  onDoubleTap: ((centroid: Offset) -> Unit)?,
  onQuickZoom: ((QuickZoomEvent) -> Unit)?,
) {
  awaitEachGesture {
    if (onTap == null && onLongPress == null && onDoubleTap == null && onQuickZoom == null) {
      // Nothing to do here. This might not be the best way to ignore a gesture though…
      awaitFirstDown(pass = PointerEventPass.Final)
      return@awaitEachGesture
    }

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
      val secondDown = if (onDoubleTap != null) {
        awaitSecondDown(firstUp = firstUp)
      } else null
      val secondDownTime = TimeSource.Monotonic.markNow()
      secondDown?.consume()

      if (secondDown == null) {
        // No valid second tap started.
        onTap?.invoke(firstUp.position)

      } else if (areWithinTouchTargetSize(firstUp, secondDown) && onQuickZoom != null) {
        val dragStart = awaitVerticalTouchSlopOrCancellation(
          pointerId = secondDown.id,
          //pointerType = secondDown.type,  // https://issuetracker.google.com/u/0/issues/348970843
        ) { change, over ->
          onQuickZoom(Zooming(secondDown.position, over.calculateQuickZoomDelta()))
          change.consume()
        }
        if (dragStart != null) {
          verticalDrag(secondDown.id) { drag ->
            onQuickZoom(Zooming(secondDown.position, drag.calculateQuickZoomDelta()))
            drag.consume()
          }
          onQuickZoom(QuickZoomStopped)

        } else if (secondDownTime.elapsedNow() < viewConfiguration.doubleTapTimeoutMillis.milliseconds) {
          onDoubleTap!!(secondDown.position)
        }
      }
    }
  }
}

private fun PointerInputChange.calculateQuickZoomDelta(): Float {
  return positionChange().y.calculateQuickZoomDelta()
}

private fun Float.calculateQuickZoomDelta(): Float {
  // This formula was copied from https://github.com/usuiat/Zoomable.
  // The coerceIn() call is important to prevent large zooms from generating
  // a < 0 zoom delta which will cause infinite/invalid zooms errors.
  return (1f + this * 0.004f).coerceIn(0.1f, 2f)
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

  data object QuickZoomStopped : QuickZoomEvent
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
