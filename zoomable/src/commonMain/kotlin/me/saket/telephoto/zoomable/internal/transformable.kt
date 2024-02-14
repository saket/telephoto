@file:Suppress("NAME_SHADOWING")

package me.saket.telephoto.zoomable.internal

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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.internal.TransformEvent.TransformDelta
import me.saket.telephoto.zoomable.internal.TransformEvent.TransformStarted
import me.saket.telephoto.zoomable.internal.TransformEvent.TransformStopped
import kotlin.math.PI
import kotlin.math.abs

// TODO: This fork of transformable() can be deleted when these are resolved:
//  - https://issuetracker.google.com/issues/278713689
//  - https://issuetracker.google.com/issues/266829800
//  - https://issuetracker.google.com/issues/266829790
/**
 * Enable transformation gestures of the modified UI element.
 *
 * Users should update their state themselves using default [TransformableState] and its
 * `onTransformation` callback or by implementing [TransformableState] interface manually and
 * reflect their own state in UI when using this component.
 *
 * This overload of transformable modifier provides [canPan] parameter, which allows the caller to
 * control when the pan can start. making pan gesture to not to start when the scale is 1f makes
 * transformable modifiers to work well within the scrollable container. See example:
 * @sample androidx.compose.foundation.samples.TransformableSampleInsideScroll
 *
 * @param state [TransformableState] of the transformable. Defines how transformation events will be
 * interpreted by the user land logic, contains useful information about on-going events and
 * provides animation capabilities.
 * @param canPan whether the pan gesture can be performed or not given the pan offset
 * @param lockRotationOnZoomPan If `true`, rotation is allowed only if touch slop is detected for
 * rotation before pan or zoom motions. If not, pan and zoom gestures will be detected, but rotation
 * gestures will not be. If `false`, once touch slop is reached, all three gestures are detected.
 * @param enabled whether zooming by gestures is enabled or not
 */
@ExperimentalFoundationApi
internal data class TransformableElement(
  private val state: TransformableState,
  private val canPan: (Offset) -> Boolean,
  private val lockRotationOnZoomPan: Boolean,
  private val enabled: Boolean,
  private val onTransformStopped: (velocity: Velocity) -> Unit = {},
): ModifierNodeElement<TransformableNode>() {
  override fun create(): TransformableNode = TransformableNode(
    state, canPan, lockRotationOnZoomPan, enabled, onTransformStopped)

  override fun update(node: TransformableNode) {
    node.update(state, canPan, lockRotationOnZoomPan, enabled, onTransformStopped)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "transformable"
    properties["state"] = state
    properties["canPan"] = canPan
    properties["enabled"] = enabled
    properties["lockRotationOnZoomPan"] = lockRotationOnZoomPan
    properties["onTransformStopped"] = onTransformStopped
  }
}

internal class TransformableNode(
  private var state: TransformableState,
  private var canPan: (Offset) -> Boolean,
  private var lockRotationOnZoomPan: Boolean,
  private var enabled: Boolean,
  private var onTransformStopped: (velocity: Velocity) -> Unit = {},
): DelegatingNode() {

  private val updatedCanPan: (Offset) -> Boolean = { canPan.invoke(it) }
  private val updatedOnTransformStopped: (Velocity) -> Unit = { onTransformStopped.invoke(it) }
  private val channel = Channel<TransformEvent>(capacity = Channel.UNLIMITED)

  private val pointerInputNode = delegate(SuspendingPointerInputModifierNode {
    if (!enabled) return@SuspendingPointerInputModifierNode
    coroutineScope {
      launch(start = CoroutineStart.UNDISPATCHED) {
        while (isActive) {
          var event = channel.receive()
          if (event !is TransformStarted) continue
          try {
            state.transform(MutatePriority.UserInput) {
              while (event !is TransformStopped) {
                (event as? TransformDelta)?.let {
                  transformBy(it.zoomChange, it.panChange, it.rotationChange, it.centroid)
                }
                event = channel.receive()
              }
            }
            (event as? TransformStopped)?.let { event ->
              updatedOnTransformStopped(event.velocity)
            }
          } catch (_: CancellationException) {
            // ignore the cancellation and start over again.
          }
        }
      }
      awaitEachGesture {
        val velocityTracker = VelocityTracker()
        var wasCancelled = false
        try {
          detectZoom(lockRotationOnZoomPan, channel, updatedCanPan, velocityTracker)
        } catch (exception: CancellationException) {
          wasCancelled = true
          if (!isActive) throw exception
        } finally {
          // todo: get this from LocalViewConfiguration.
          val maximumVelocity = Velocity(Int.MAX_VALUE.toFloat(), Int.MAX_VALUE.toFloat())
          val velocity = if (wasCancelled) Velocity.Zero else velocityTracker.calculateVelocity(maximumVelocity)
          channel.trySend(TransformStopped(velocity))
        }
      }
    }
  })

  fun update(
    state: TransformableState,
    canPan: (Offset) -> Boolean,
    lockRotationOnZoomPan: Boolean,
    enabled: Boolean,
    onTransformStopped: (velocity: Velocity) -> Unit,
  ) {
    this.canPan = canPan
    this.onTransformStopped = onTransformStopped
    val needsReset = this.state != state ||
      this.enabled != enabled ||
      this.lockRotationOnZoomPan != lockRotationOnZoomPan
    if (needsReset) {
      this.state = state
      this.enabled = enabled
      this.lockRotationOnZoomPan = lockRotationOnZoomPan
      pointerInputNode.resetPointerInputHandler()
    }
  }
}

private suspend fun AwaitPointerEventScope.detectZoom(
  panZoomLock: Boolean,
  channel: Channel<TransformEvent>,
  canPan: (Offset) -> Boolean,
  velocityTracker: VelocityTracker,
) {
  var rotation = 0f
  var zoom = 1f
  var pan = Offset.Zero
  var pastTouchSlop = false
  val touchSlop = viewConfiguration.touchSlop
  var lockedToPanZoom = false
  awaitFirstDown(requireUnconsumed = false)
  do {
    val event = awaitPointerEvent()
    val canceled = event.changes.fastAny { it.isConsumed }
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

        if (event.changes.size > 1 ||
          zoomMotion > touchSlop ||
          rotationMotion > touchSlop ||
          (panMotion > touchSlop && canPan.invoke(panChange))
        ) {
          pastTouchSlop = true
          lockedToPanZoom = panZoomLock && rotationMotion < touchSlop
          channel.trySend(TransformStarted)
        }
      }

      if (pastTouchSlop) {
        val centroid = event.calculateCentroid(useCurrent = false)
        val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange
        if (effectiveRotation != 0f ||
          zoomChange != 1f ||
          (panChange != Offset.Zero && canPan.invoke(panChange))
        ) {
          channel.trySend(TransformDelta(zoomChange, panChange, effectiveRotation, centroid))
        }
        event.changes.fastForEach {
          if (it.positionChanged()) {
            it.consume()
          }
        }
      }
    } else {
      channel.trySend(TransformStopped(Velocity.Zero))
    }
    val finalEvent = awaitPointerEvent(pass = PointerEventPass.Final)
    // someone consumed while we were waiting for touch slop
    val finallyCanceled = finalEvent.changes.fastAny { it.isConsumed } && !pastTouchSlop
  } while (!canceled && !finallyCanceled && event.changes.fastAny { it.pressed })
}

private sealed class TransformEvent {
  object TransformStarted : TransformEvent()
  class TransformStopped(val velocity: Velocity) : TransformEvent()
  class TransformDelta(
    val zoomChange: Float,
    val panChange: Offset,
    val rotationChange: Float,
    val centroid: Offset,
  ) : TransformEvent()
}

// Workaround for https://github.com/saket/telephoto/issues/53.
// Also see https://issuetracker.google.com/issues/309841148.
private fun VelocityTracker.calculateVelocity(maximumVelocity: Velocity): Velocity {
  val calculated = calculateVelocity()
  return Velocity(
    calculated.x.coerceIn(-maximumVelocity.x, maximumVelocity.x),
    calculated.y.coerceIn(-maximumVelocity.y, maximumVelocity.y),
  )
}
