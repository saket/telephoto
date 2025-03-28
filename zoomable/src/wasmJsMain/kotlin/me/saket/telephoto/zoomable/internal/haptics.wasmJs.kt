package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

internal actual fun CompositionLocalConsumerModifierNode.hapticFeedbackPerformer(): HapticFeedbackPerformer {
  return HapticFeedbackPerformer { /* No haptics on desktop */ }
}
