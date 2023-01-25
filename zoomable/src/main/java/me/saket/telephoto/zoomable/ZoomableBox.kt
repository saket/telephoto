package me.saket.telephoto.zoomable

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.animateRotateBy
import androidx.compose.foundation.gestures.animateZoomBy
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun ZoomableBox(
  state: ZoomableState,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val transformableState = rememberTransformableState { zoomChange, offsetChange, rotationChange ->
    state.transformations = state.transformations.let {
      it.copy(
        scale = it.scale * zoomChange,
        rotationZ = it.rotationZ + rotationChange,
        offset = it.offset + offsetChange
      )
    }
  }

  Box(
    modifier = modifier
      .transformable(transformableState)
      .pointerInput(Unit) {
        forEachGesture {
          awaitPointerEventScope {
            awaitFirstDown(requireUnconsumed = false)
            awaitAllPointersUp()
          }
          val by = -state.transformations.rotationZ.rem(360f)
          val byScale = if (state.transformations.scale < 1f) 1f / state.transformations.scale else 0f

          transformableState.animateRotateAndZoomBy(
            degrees = by,
            zoomFactor = byScale
          )
        }
      },
    content = { content() }
  )
}

/**
 * Animate rotate by a ratio of [degrees] clockwise and zoom by a ratio of
 * [zoomFactor] over the current size. Suspend until both animations are finished.
 *
 * Combines from [animateRotateBy] and [animateZoomBy].
 *
 * @param degrees ratio over the current size by which to rotate, in degrees.
 * @param zoomFactor ratio over the current size by which to zoom. For example, if [zoomFactor]
 * is `3f`, zoom will be increased 3 fold from the current value.
 * @param animationSpec [AnimationSpec] to be used for animation
 */
suspend fun TransformableState.animateRotateAndZoomBy(
  degrees: Float,
  zoomFactor: Float,
  animationSpec: AnimationSpec<Float> = spring()
) {
  if (zoomFactor <= 0) {
    animateRotateBy(degrees, animationSpec)
    return
  }

  var previousRotation = 0f
  var previousZoom = 1f

  transform {
    val rotationAnimation = AnimationState(initialValue = previousRotation)
    val zoomAnimation = AnimationState(initialValue = previousZoom)

    coroutineScope {
      launch {
        rotationAnimation.animateTo(degrees, animationSpec) {
          val delta = this.value - previousRotation
          transformBy(rotationChange = delta)
          previousRotation = this.value
        }
      }
      launch {
        zoomAnimation.animateTo(zoomFactor, animationSpec) {
          val scaleFactor = if (previousZoom == 0f) 1f else this.value / previousZoom
          transformBy(zoomChange = scaleFactor)
          previousZoom = this.value
        }
      }
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
