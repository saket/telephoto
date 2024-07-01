package me.saket.telephoto.zoomable.internal

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView

internal actual fun CompositionLocalConsumerModifierNode.hapticFeedbackPerformer(): HapticFeedbackPerformer {
  return HapticFeedbackPerformer {
    currentValueOf(LocalView).performHapticFeedback(
      if (Build.VERSION.SDK_INT >= 30) {
        HapticFeedbackConstants.GESTURE_END
      } else if (Build.VERSION.SDK_INT >= 23) {
        // PhoneWindowManager#getVibrationEffect() maps
        // GESTURE_END and CONTEXT_CLICK to the same effect.
        HapticFeedbackConstants.CONTEXT_CLICK
      } else {
        // PhoneWindowManager#getVibrationEffect() maps
        // GESTURE_END and CLOCK_TICK to the same effect.
        HapticFeedbackConstants.CLOCK_TICK
      }
    )
  }
}
