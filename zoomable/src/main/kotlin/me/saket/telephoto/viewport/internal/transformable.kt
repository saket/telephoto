@file:Suppress("NAME_SHADOWING")

package me.saket.telephoto.viewport.internal

/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs

/**
 * Enable transformation gestures of the modified UI element.
 *
 * Users should update their state themselves using default [TransformableState] and its
 * `onTransformation` callback or by implementing [TransformableState] interface manually and
 * reflect their own state in UI when using this component.
 *
 * @sample androidx.compose.foundation.samples.TransformableSample
 *
 * @param state [TransformableState] of the transformable. Defines how transformation events will be
 * interpreted by the user land logic, contains useful information about on-going events and
 * provides animation capabilities.
 * @param lockRotationOnZoomPan If `true`, rotation is allowed only if touch slop is detected for
 * rotation before pan or zoom motions. If not, pan and zoom gestures will be detected, but rotation
 * gestures will not be. If `false`, once touch slop is reached, all three gestures are detected.
 * @param enabled whether zooming by gestures is enabled or not
 */
fun Modifier.transformable(
  state: TransformableState,
  lockRotationOnZoomPan: Boolean = false,
  onTransformStopped: (velocity: Velocity) -> Unit = {},
  enabled: Boolean = true,
) = composed(
  factory = {
    val updatePanZoomLock = rememberUpdatedState(lockRotationOnZoomPan)
    val updatedOnGestureEnd = rememberUpdatedState(onTransformStopped)
    val channel = remember { Channel<TransformEvent>(capacity = Channel.UNLIMITED) }
    if (enabled) {
      LaunchedEffect(state) {
        while (isActive) {
          var event = channel.receive()
          if (event !is TransformEvent.TransformStarted) continue
          try {
            state.transform(MutatePriority.UserInput) {
              while (event !is TransformEvent.TransformStopped) {
                (event as? TransformEvent.TransformDelta)?.let {
                  transformBy(it.zoomChange, it.panChange, it.rotationChange, it.centroid)
                }
                event = channel.receive()
              }
            }
            (event as? TransformEvent.TransformStopped)?.let { event ->
              updatedOnGestureEnd.value(event.velocity)
            }
          } catch (_: CancellationException) {
            // ignore the cancellation and start over again.
          }
        }
      }
    }
    val block: suspend PointerInputScope.() -> Unit = remember {
      {
        coroutineScope {
          awaitEachGesture {
            val velocityTracker = VelocityTracker()
            var wasGestureCancelled = false
            try {
              detectZoom(
                panZoomLock = updatePanZoomLock,
                channel = channel,
                canConsumePanChange = state::canConsumePanChange,
                velocityTracker = velocityTracker,
              )
            } catch (exception: CancellationException) {
              wasGestureCancelled = true
              if (!isActive) throw exception
            } finally {
              val velocity = if (wasGestureCancelled) Velocity.Zero else velocityTracker.calculateVelocity()
              channel.trySend(TransformEvent.TransformStopped(velocity))
            }
          }
        }
      }
    }
    if (enabled) Modifier.pointerInput(Unit, block) else Modifier
  },
  inspectorInfo = debugInspectorInfo {
    name = "transformable"
    properties["state"] = state
    properties["enabled"] = enabled
    properties["lockRotationOnZoomPan"] = lockRotationOnZoomPan
  }
)

private sealed class TransformEvent {
  object TransformStarted : TransformEvent()
  data class TransformStopped(val velocity: Velocity) : TransformEvent()
  class TransformDelta(
    val zoomChange: Float,
    val panChange: Offset,
    val rotationChange: Float,
    val centroid: Offset,
  ) : TransformEvent()
}

private suspend fun AwaitPointerEventScope.detectZoom(
  panZoomLock: State<Boolean>,
  channel: Channel<TransformEvent>,
  canConsumePanChange: (Offset) -> Boolean,
  velocityTracker: VelocityTracker,
) {
  var rotation = 0f
  var zoom = 1f
  var pan = Offset.Zero
  var pastTouchSlop = false
  val touchSlop = viewConfiguration.touchSlop
  var lockedToPanZoom = false
  var ignoringGesture = false
  awaitFirstDown(requireUnconsumed = false)
  do {
    val event = awaitPointerEvent()
    val canceled = ignoringGesture || event.changes.fastAny { it.isConsumed }
    if (!canceled) {
      event.changes.fastForEach {
        velocityTracker.addPointerInputChange(it)
      }

      val zoomChange = event.calculateZoom()
      val rotationChange = event.calculateRotation()
      val panChange = event.calculatePan()

      if (!pastTouchSlop) {
        zoom *= zoomChange
        rotation += rotationChange
        pan += panChange

        val centroidSize = event.calculateCentroidSize(useCurrent = false)
        val zoomMotion = abs(1 - zoom) * centroidSize
        val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
        val panMotion = pan.getDistance()

        if (zoomMotion > touchSlop ||
          rotationMotion > touchSlop ||
          panMotion > touchSlop
        ) {
          // A positive zoom motion indicates the presence of multi-touch for consuming this gesture right away.
          if (zoomMotion > 0f || canConsumePanChange(pan)) {
            pastTouchSlop = true
            lockedToPanZoom = panZoomLock.value && rotationMotion < touchSlop
            channel.trySend(TransformEvent.TransformStarted)
          } else {
            ignoringGesture = true
          }
        }
      }

      if (pastTouchSlop) {
        val centroid = event.calculateCentroid(useCurrent = false)
        val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange
        if (effectiveRotation != 0f ||
          zoomChange != 1f ||
          panChange != Offset.Zero
        ) {
          channel.trySend(TransformEvent.TransformDelta(zoomChange, panChange, effectiveRotation, centroid))
        }
        event.changes.fastForEach {
          if (it.positionChanged()) {
            it.consume()
          }
        }
      }
    }
  } while (!canceled && event.changes.fastAny { it.pressed })
}
