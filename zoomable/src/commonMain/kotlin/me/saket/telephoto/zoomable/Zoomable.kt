package me.saket.telephoto.zoomable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.internal.MutatePriorities
import me.saket.telephoto.zoomable.internal.rememberHapticFeedbackPerformer
import me.saket.telephoto.zoomable.internal.stopTransformation
import me.saket.telephoto.zoomable.internal.tappableAndQuickZoomable
import me.saket.telephoto.zoomable.internal.transformable

/**
 * A `Modifier` for handling pan & zoom gestures, designed to be shared across all your media
 * composables so that your users can use the same familiar gestures throughout your app. It offers,
 *
 * - Pinch to zoom and flings
 * - Double tap to zoom
 * - Single finger zoom (double tap and hold)
 * - Haptic feedback for over/under zoom
 * - Compatibility with nested scrolling
 * - Click listeners
 *
 * Because `Modifier.zoomable()` consumes all gestures including double-taps, [Modifier.clickable] and
 * [Modifier.combinedClickable] will not work on the composable this `Modifier.zoomable()` is applied to.
 * As an alternative, [onClick] and [onLongClick] parameters can be used instead.
 *
 * @param enabled whether or not gestures are enabled.
 *
 * @param clipToBounds defaults to true to act as a reminder that this layout should probably fill all
 * available space. Otherwise, gestures made outside the composable's layout bounds will not be registered.
 * */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.zoomable(
  state: ZoomableState,
  enabled: Boolean = true,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
): Modifier = composed {
  val zoomableModifier = if (state.isReadyToInteract) {
    val hapticFeedbackPerformer = rememberHapticFeedbackPerformer()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    transformable(
      state = state.transformableState,
      canPan = state::canConsumePanChange,
      enabled = enabled,
      onTransformStopped = { velocity ->
        scope.launch {
          if (state.isZoomOutsideRange()) {
            hapticFeedbackPerformer.performHapticFeedback()
            state.smoothlySettleZoomOnGestureEnd()
          } else {
            state.fling(velocity = velocity, density = density)
          }
        }
      }
    ).tappableAndQuickZoomable(
      gesturesEnabled = enabled,
      transformableState = state.transformableState,
      onPress = {
        scope.launch {
          state.transformableState.stopTransformation(MutatePriorities.FlingAnimation)
        }
      },
      onTap = onClick,
      onLongPress = onLongClick,
      onDoubleTap = { centroid ->
        scope.launch {
          state.handleDoubleTapZoomTo(centroid = centroid)
        }
      },
      onQuickZoomStopped = {
        if (state.isZoomOutsideRange()) {
          scope.launch {
            hapticFeedbackPerformer.performHapticFeedback()
            state.smoothlySettleZoomOnGestureEnd()
          }
        }
      },
    )
  } else {
    Modifier
  }

  this
    .let { if (clipToBounds) it.clipToBounds() else it }
    .onSizeChanged { state.contentLayoutSize = it.toSize() }
    .then(zoomableModifier)
    .then(
      if (state.autoApplyTransformations) {
        Modifier.applyTransformation(state.contentTransformation)
      } else {
        Modifier
      }
    )
}
