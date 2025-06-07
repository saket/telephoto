package me.saket.telephoto.zoomable.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

internal expect fun CompositionLocalConsumerModifierNode.hapticFeedbackPerformer(): HapticFeedbackPerformer

@Composable
internal expect fun rememberHapticFeedbackPerformer(): HapticFeedbackPerformer

/**
 * Alternative to [HapticFeedback] until it
 * [supports all possible feedback constants](https://issuetracker.google.com/issues/195043382).
 */
internal fun interface HapticFeedbackPerformer {
  suspend fun performHapticFeedback(effect: HapticEffect)

  companion object {
    val NoOp = HapticFeedbackPerformer {}
  }
}

internal sealed interface HapticEffect {
  data class ContinuousRubberBanding(val intensityRatio: () -> Float) : HapticEffect
  data object GestureThresholdCrossed : HapticEffect
  data object Overzoom : HapticEffect
  data object None : HapticEffect
}
