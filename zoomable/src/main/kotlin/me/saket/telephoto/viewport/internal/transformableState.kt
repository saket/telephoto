package me.saket.telephoto.viewport.internal

/*
 * Copyright 2021 The Android Open Source Project
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

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.coroutineScope

/**
 * State of [transformable]. Allows for a granular control of how different gesture
 * transformations are consumed by the user as well as to write custom transformation methods
 * using [transform] suspend function.
 */
@JvmDefaultWithCompatibility
interface TransformableState {
  /**
   * Call this function to take control of transformations and gain the ability to send transform
   * events via [TransformScope.transformBy]. All actions that change zoom, pan or rotation
   * values must be performed within a [transform] block (even if they don't call any other
   * methods on this object) in order to guarantee that mutual exclusion is enforced.
   *
   * If [transform] is called from elsewhere with the [transformPriority] higher or equal to
   * ongoing transform, ongoing transform will be canceled.
   */
  suspend fun transform(
    transformPriority: MutatePriority = MutatePriority.Default,
    block: suspend TransformScope.() -> Unit
  )

  /**
   * Whether this [TransformableState] is capable of consuming a pan change. Called at the starting
   * of each gesture to determine if the gesture can be handled or given up to the parent for
   * nested scrolling.
   *
   * @return false to skip this gesture.
   * */
  fun canConsumePanChange(panChange: Offset): Boolean

  /**
   * Whether this [TransformableState] is currently transforming by gesture or programmatically or
   * not.
   */
  val isTransformInProgress: Boolean
}

/**
 * Scope used for suspending transformation operations
 */
@JvmDefaultWithCompatibility
interface TransformScope {
  /**
   * Attempts to transform by [zoomChange] in relative multiplied value, by [panChange] in
   * pixels and by [rotationChange] in degrees.
   *
   * @param zoomChange scale factor multiplier change for zoom
   * @param panChange panning offset change, in [Offset] pixels
   * @param rotationChange change of the rotation in degrees
   */
  fun transformBy(
    zoomChange: Float = 1f,
    panChange: Offset = Offset.Zero,
    rotationChange: Float = 0f,
    centroid: Offset = Offset.Zero,
  )
}

/**
 * Default implementation of [TransformableState] interface that contains necessary information
 * about the ongoing transformations and provides smooth transformation capabilities.
 *
 * This is the simplest way to set up a [transformable] modifier. When constructing this
 * [TransformableState], you must provide a [onTransformation] lambda, which will be invoked
 * whenever pan, zoom or rotation happens (by gesture input or any [TransformableState.transform]
 * call) with the deltas from the previous event.
 *
 * @param canConsumePanChange See [TransformableState.canConsumePanChange].
 *
 * @param onTransformation callback invoked when transformation occurs. The callback receives the
 * change from the previous event. It's relative scale multiplier for zoom, [Offset] in pixels
 * for pan and degrees for rotation. Callers should update their state in this lambda.
 */
fun TransformableState(
  canConsumePanChange: (panChange: Offset) -> Boolean,
  onTransformation: (zoomChange: Float, panChange: Offset, rotationChange: Float, centroid: Offset) -> Unit
): TransformableState = DefaultTransformableState(canConsumePanChange, onTransformation)

/**
 * Create and remember default implementation of [TransformableState] interface that contains
 * necessary information about the ongoing transformations and provides smooth transformation
 * capabilities.
 *
 * This is the simplest way to set up a [transformable] modifier. When constructing this
 * [TransformableState], you must provide a [onTransformation] lambda, which will be invoked
 * whenever pan, zoom or rotation happens (by gesture input or any [TransformableState.transform]
 * call) with the deltas from the previous event.
 *
 * @param canConsumePanChange See [TransformableState.canConsumePanChange].
 *
 * @param onTransformation callback invoked when transformation occurs. The callback receives the
 * change from the previous event. It's relative scale multiplier for zoom, [Offset] in pixels
 * for pan and degrees for rotation. Callers should update their state in this lambda.
 */
@Composable
@Suppress("NAME_SHADOWING")
fun rememberTransformableState(
  canConsumePanChange: (panChange: Offset) -> Boolean,
  onTransformation: (zoomChange: Float, panChange: Offset, rotationChange: Float, centroid: Offset) -> Unit
): TransformableState {
  val canConsumePanChange = rememberUpdatedState(canConsumePanChange)
  val onTransformation = rememberUpdatedState(onTransformation)
  return remember {
    TransformableState(
      canConsumePanChange = { p -> canConsumePanChange.value.invoke(p) },
      onTransformation = { z, p, r, c -> onTransformation.value.invoke(z, p, r, c) }
    )
  }
}

/**
 * Animate zoom by a ratio of [zoomFactor] over the current size and suspend until its finished.
 *
 * @param zoomFactor ratio over the current size by which to zoom. For example, if [zoomFactor]
 * is `3f`, zoom will be increased 3 fold from the current value.
 * @param animationSpec [AnimationSpec] to be used for animation
 */
suspend fun TransformableState.animateZoomBy(
  zoomFactor: Float,
  animationSpec: AnimationSpec<Float> = SpringSpec(stiffness = Spring.StiffnessLow)
) {
  require(zoomFactor > 0) {
    "zoom value should be greater than 0"
  }
  var previous = 1f
  transform {
    AnimationState(initialValue = previous).animateTo(zoomFactor, animationSpec) {
      val scaleFactor = if (previous == 0f) 1f else this.value / previous
      transformBy(zoomChange = scaleFactor)
      previous = this.value
    }
  }
}

/**
 * Animate rotate by a ratio of [degrees] clockwise and suspend until its finished.
 *
 * @param degrees ratio over the current size by which to rotate, in degrees
 * @param animationSpec [AnimationSpec] to be used for animation
 */
suspend fun TransformableState.animateRotateBy(
  degrees: Float,
  animationSpec: AnimationSpec<Float> = SpringSpec(stiffness = Spring.StiffnessLow)
) {
  var previous = 0f
  transform {
    AnimationState(initialValue = previous).animateTo(degrees, animationSpec) {
      val delta = this.value - previous
      transformBy(rotationChange = delta)
      previous = this.value
    }
  }
}

/**
 * Animate pan by [offset] Offset in pixels and suspend until its finished
 *
 * @param offset offset to pan, in pixels
 * @param animationSpec [AnimationSpec] to be used for pan animation
 */
suspend fun TransformableState.animatePanBy(
  offset: Offset,
  animationSpec: AnimationSpec<Offset> = SpringSpec(stiffness = Spring.StiffnessLow)
) {
  var previous = Offset.Zero
  transform {
    AnimationState(
      typeConverter = Offset.VectorConverter,
      initialValue = previous
    )
      .animateTo(offset, animationSpec) {
        val delta = this.value - previous
        transformBy(panChange = delta)
        previous = this.value
      }
  }
}

/**
 * Zoom without animation by a ratio of [zoomFactor] over the current size and suspend until it's
 * set.
 *
 * @param zoomFactor ratio over the current size by which to zoom
 */
suspend fun TransformableState.zoomBy(zoomFactor: Float) = transform {
  transformBy(zoomFactor, Offset.Zero, 0f)
}

/**
 * Rotate without animation by a [degrees] degrees and suspend until it's set.
 *
 * @param degrees degrees by which to rotate
 */
suspend fun TransformableState.rotateBy(degrees: Float) = transform {
  transformBy(1f, Offset.Zero, degrees)
}

/**
 * Pan without animation by a [offset] Offset in pixels and suspend until it's set.
 *
 * @param offset offset in pixels by which to pan
 */
suspend fun TransformableState.panBy(offset: Offset) = transform {
  transformBy(1f, offset, 0f)
}

/**
 * Stop and suspend until any ongoing [TransformableState.transform] with priority
 * [terminationPriority] or lower is terminated.
 *
 * @param terminationPriority transformation that runs with this priority or lower will be stopped
 */
suspend fun TransformableState.stopTransformation(
  terminationPriority: MutatePriority = MutatePriority.Default
) {
  this.transform(terminationPriority) {
    // do nothing, just lock the mutex so other scroll actors are cancelled
  }
}

private class DefaultTransformableState(
  val canConsumePanChange: (panChange: Offset) -> Boolean,
  val onTransformation: (zoomChange: Float, panChange: Offset, rotationChange: Float, centroid: Offset) -> Unit
) : TransformableState {

  private val transformScope: TransformScope = object : TransformScope {
    override fun transformBy(zoomChange: Float, panChange: Offset, rotationChange: Float, centroid: Offset) =
      onTransformation(zoomChange, panChange, rotationChange, centroid)
  }

  private val transformMutex = MutatorMutex()

  private val isTransformingState = mutableStateOf(false)

  override suspend fun transform(
    transformPriority: MutatePriority,
    block: suspend TransformScope.() -> Unit
  ): Unit = coroutineScope {
    transformMutex.mutateWith(transformScope, transformPriority) {
      isTransformingState.value = true
      try {
        block()
      } finally {
        isTransformingState.value = false
      }
    }
  }

  override fun canConsumePanChange(panChange: Offset): Boolean {
    return canConsumePanChange.invoke(panChange)
  }

  override val isTransformInProgress: Boolean
    get() = isTransformingState.value
}
