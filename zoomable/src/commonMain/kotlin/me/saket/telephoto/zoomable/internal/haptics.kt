package me.saket.telephoto.zoomable.internal

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberHapticFeedbackPerformer(): HapticFeedbackPerformer

internal interface HapticFeedbackPerformer {
  fun performHapticFeedback()
}
