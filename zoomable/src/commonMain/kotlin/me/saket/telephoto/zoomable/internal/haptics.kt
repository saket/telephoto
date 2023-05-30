package me.saket.telephoto.zoomable.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback

@Composable
internal expect fun rememberHapticFeedbackPerformer(): HapticFeedbackPerformer

/** Migrate to [HapticFeedback] once it supports the constant(s) we want */
internal interface HapticFeedbackPerformer {
  fun performHapticFeedback()
}
