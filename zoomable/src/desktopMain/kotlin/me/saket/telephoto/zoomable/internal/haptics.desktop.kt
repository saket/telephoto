package me.saket.telephoto.zoomable.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

internal actual fun CompositionLocalConsumerModifierNode.hapticFeedbackPerformer(): HapticFeedbackPerformer {
  return HapticFeedbackPerformer.NoOp // No haptics on desktop.
}

@Composable
internal actual fun rememberHapticFeedbackPerformer(): HapticFeedbackPerformer {
  return HapticFeedbackPerformer.NoOp // No haptics on desktop.
}
