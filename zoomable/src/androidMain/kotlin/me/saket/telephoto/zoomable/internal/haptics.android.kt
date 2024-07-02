package me.saket.telephoto.zoomable.internal

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView

internal actual fun CompositionLocalConsumerModifierNode.hapticFeedbackPerformer(): HapticFeedbackPerformer {
  return HapticFeedbackPerformer {
    currentValueOf(LocalView).performHapticFeedback(
      when {
        Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.GESTURE_END
        Build.VERSION.SDK_INT >= 23 -> HapticFeedbackConstants.CONTEXT_CLICK // Same effect as GESTURE_END.
        else -> HapticFeedbackConstants.CLOCK_TICK // Same effect as GESTURE_END.
      }
    )
  }
}
