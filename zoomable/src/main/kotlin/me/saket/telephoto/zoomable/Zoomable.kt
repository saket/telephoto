@file:Suppress("NAME_SHADOWING")

package me.saket.telephoto.zoomable

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.internal.MutatePriorities
import me.saket.telephoto.zoomable.internal.doubleTapZoomable
import me.saket.telephoto.zoomable.internal.stopTransformation
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
fun Modifier.zoomable(
  state: ZoomableState,
  enabled: Boolean = true,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
): Modifier = composed {
  val onClick by rememberUpdatedState(onClick)

  val zoomableModifier = if (state.isReadyToInteract) {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var isQuickZooming by remember { mutableStateOf(false) }

    Modifier
      .transformable(
        state = state.transformableState,
        canPan = state::canConsumePanChange,
        enabled = enabled,
        onTransformStopped = { velocity ->
          scope.launch {
            if (state.isZoomOutsideRange()) {
              view.performHapticFeedback(HapticFeedbackConstantsCompat.GESTURE_END)
              state.smoothlySettleZoomOnGestureEnd()
            } else {
              state.fling(velocity = velocity, density = density)
            }
          }
        }
      )
      .pointerInput(enabled) {
        detectTapGestures(
          onPress = {
            state.transformableState.stopTransformation(MutatePriorities.FlingAnimation)
          },
          onTap = {
            // Make sure this wasn't actually a quick zoom gesture. When a
            // quick zoom gesture is started, detectTapGestures() detects that
            // the drag event was consumed and decides to treat it as a single tap.
            if (!isQuickZooming) {
              onClick?.invoke(it)
            }
          },
          onLongPress = onLongClick,
          onDoubleTap = {
            // Double tap gestures can't be handled here because it conflicts with
            // detection of double tap and hold gestures. Still, an empty lambda is
            // necessary to force detectTapGestures() to delay its detection of taps
            // to let Modifier.doubleTapZoomable() detect double taps.
          }
        )
      }
      .doubleTapZoomable(
        enabled = enabled,
        state = state.transformableState,
        onQuickZoomStarted = { isQuickZooming = true },
        onQuickZoomStopped = {
          scope.launch {
            isQuickZooming = false
            if (state.isZoomOutsideRange()) {
              view.performHapticFeedback(HapticFeedbackConstantsCompat.GESTURE_END)
              state.smoothlySettleZoomOnGestureEnd()
            }
          }
        },
        onDoubleTap = { centroid ->
          scope.launch {
            state.handleDoubleTapZoomTo(centroid = centroid)
          }
        }
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

// Can be removed once https://issuetracker.google.com/u/1/issues/195043382 is fixed.
private object HapticFeedbackConstantsCompat {
  val GESTURE_END: Int
    get() {
      return if (Build.VERSION.SDK_INT >= 30) {
        HapticFeedbackConstants.GESTURE_END
      } else {
        // PhoneWindowManager#getVibrationEffect() maps
        // GESTURE_END and CONTEXT_CLICK to the same effect.
        HapticFeedbackConstants.CONTEXT_CLICK
      }
    }
}
