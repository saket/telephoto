package me.saket.telephoto.zoomable.internal

import androidx.compose.runtime.Composable

/** No haptics on desktop */
@Composable
internal actual fun rememberHapticFeedbackPerformer(): HapticFeedbackPerformer {
  return NoOpHapticFeedbackPerformer
}

private object NoOpHapticFeedbackPerformer : HapticFeedbackPerformer {
  override fun performHapticFeedback() {
    TODO("Not yet implemented")
  }
}
