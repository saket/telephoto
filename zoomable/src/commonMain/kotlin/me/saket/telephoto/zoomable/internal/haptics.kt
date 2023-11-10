package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

internal expect fun CompositionLocalConsumerModifierNode.hapticFeedbackPerformer(): HapticFeedbackPerformer

/** Alternative to [HapticFeedback] until it supports all possible feedback constants. */
internal interface HapticFeedbackPerformer {
  fun performHapticFeedback()
}
