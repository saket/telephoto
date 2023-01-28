package me.saket.telephoto.zoomable

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.animatePanBy
import androidx.compose.foundation.gestures.animateRotateBy
import androidx.compose.foundation.gestures.animateZoomBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.coroutineScope
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
  val zoomableModifier = if (state.contentSize.isSpecified) {
    val transformableState = rememberTransformableState { zoomChange, offsetChange, rotationChange ->
      state.transformations = state.transformations.let {
        it.copy(
          scale = it.scale * zoomChange,
          rotationZ = if (state.rotationEnabled) it.rotationZ + rotationChange else 0f,
          offset = it.offset + offsetChange,
          transformOrigin = TransformOrigin.Center
        )
      }
    }

    val scope = rememberCoroutineScope()

    Modifier
      .let { if (clipToBounds) it.clipToBounds() else it }
      .transformable(transformableState)
      .onAllPointersUp {
        // Reset is performed on an independent scope, but the animation will be
        // canceled if TransformableState#transform() is called from anywhere else.
        scope.launch {
          transformableState.animateRotateAndZoomBy(
            degrees = -state.transformations.rotationZ.rem(360f),
            zoomFactor = if (state.transformations.scale < 1f) 1f / state.transformations.scale else 0f,
            // todo: this isn't perfect. pan should only reset if
            //  it's content edges don't overlap with this layout's edges.
            offset = -state.transformations.offset,
          )
        }
      }

  } else {
    Modifier
  }

  Box(
    modifier = modifier.then(zoomableModifier),
    content = { content() }
  )
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

/** Combines [animateRotateBy], [animateZoomBy] and [animatePanBy]. */
internal suspend fun TransformableState.animateRotateAndZoomBy(
  degrees: Float,
  zoomFactor: Float,
  offset: Offset,
) {
  var previousRotation = 0f
  var previousZoom = 1f
  var previousPan = Offset.Zero

  transform {
    val rotationAnimation = AnimationState(initialValue = previousRotation)
    val zoomAnimation = AnimationState(initialValue = previousZoom)
    val panAnimation = AnimationState(Offset.VectorConverter, initialValue = previousPan)

    // TODO: reduce three animations into one. Example code in https://issuetracker.google.com/u/1/issues/266807251.
    coroutineScope {
      launch {
        rotationAnimation.animateTo(degrees, spring()) {
          val delta = this.value - previousRotation
          transformBy(rotationChange = delta)
          previousRotation = this.value
        }
      }
      if (zoomFactor > 0f) {
        launch {
          zoomAnimation.animateTo(zoomFactor, spring()) {
            val scaleFactor = if (previousZoom == 0f) 1f else this.value / previousZoom
            transformBy(zoomChange = scaleFactor)
            previousZoom = this.value
          }
        }
      }
      launch {
        panAnimation.animateTo(offset, spring()) {
          val delta = this.value - previousPan
          transformBy(panChange = delta)
          previousPan = this.value
        }
      }
    }
  }
}
