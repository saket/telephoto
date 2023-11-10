package me.saket.telephoto.zoomable.internal

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView

internal actual fun CompositionLocalConsumerModifierNode.hapticFeedbackPerformer(): HapticFeedbackPerformer {
  return object : HapticFeedbackPerformer {
    override fun performHapticFeedback() {
      currentValueOf(LocalView).performHapticFeedback(HapticFeedbackConstantsCompat.GESTURE_END)
    }
  }
}

// Can be removed once https://issuetracker.google.com/issues/195043382 is fixed.
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
