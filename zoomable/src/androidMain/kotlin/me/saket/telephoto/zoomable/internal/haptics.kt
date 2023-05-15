package me.saket.telephoto.zoomable.internal

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun rememberHapticFeedbackPerformer(): HapticFeedbackPerformer {
  val view = LocalView.current

  return remember(view) {
    object : HapticFeedbackPerformer {
      override fun performHapticFeedback() {
        view.performHapticFeedback(HapticFeedbackConstantsCompat.GESTURE_END)
      }
    }
  }
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
