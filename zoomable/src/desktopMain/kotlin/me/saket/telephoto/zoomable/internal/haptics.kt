package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

/** No haptics on desktop */
internal actual fun CompositionLocalConsumerModifierNode.hapticFeedbackPerformer(): HapticFeedbackPerformer {
  return NoOpHapticFeedbackPerformer
}

private object NoOpHapticFeedbackPerformer : HapticFeedbackPerformer {
  override fun performHapticFeedback() = Unit
}
