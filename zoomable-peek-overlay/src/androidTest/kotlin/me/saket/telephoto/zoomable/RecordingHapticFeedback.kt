package me.saket.telephoto.zoomable

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

internal class RecordingHapticFeedback : HapticFeedback {
  var performedFeedbacks = ArrayDeque<HapticFeedbackType>()

  override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
    performedFeedbacks.add(hapticFeedbackType)
  }
}
