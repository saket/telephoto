package me.saket.telephoto.zoomable

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberCoroutineScope
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
import me.saket.telephoto.zoomable.internal.transformable

// todo: doc.
/**
 * A building block for designing zoomable media experiences.
 *
 * Because `Modifier.zoomable()` consumes all gestures including double-taps, [Modifier.clickable] and
 * [Modifier.combinedClickable] will not work on the composable `Modifier.zoomable()` is applied to.
 * As an alternative, [onClick] and [onLongClick] parameters can be used instead.
 *
 * @param clipToBounds Defaults to true to act as a reminder that this layout should fill all available
 * space. Otherwise, gestures made outside the composable's layout bounds will not be registered.
 * */
fun Modifier.zoomable(
  state: ZoomableState,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
): Modifier = composed {
  val zoomableModifier = if (state.isReadyToInteract) {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    Modifier
      .transformable(
        state = state.transformableState,
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
      .pointerInput(Unit) {
        detectTapGestures(
          onTap = onClick,
          onLongPress = onLongClick,
          onDoubleTap = { centroid ->
            scope.launch {
              state.handleDoubleTapZoomTo(centroid = centroid)
            }
          }
        )
      }
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
