package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

internal expect fun CompositionLocalConsumerModifierNode.hapticFeedbackPerformer(): HapticFeedbackPerformer

/**
 * Alternative to [HapticFeedback] until it
 * [supports all possible feedback constants](https://issuetracker.google.com/issues/195043382).
 */
internal fun interface HapticFeedbackPerformer {
  fun performHapticFeedback()
}
