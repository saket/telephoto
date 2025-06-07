package me.saket.telephoto.zoomable.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

internal actual fun CompositionLocalConsumerModifierNode.hapticFeedbackPerformer(): HapticFeedbackPerformer {
  return UiKitHapticFeedbackPerformer()
}

@Composable
internal actual fun rememberHapticFeedbackPerformer(): HapticFeedbackPerformer {
  return remember { UiKitHapticFeedbackPerformer() }
}

private class UiKitHapticFeedbackPerformer : HapticFeedbackPerformer {
  override suspend fun performHapticFeedback(effect: HapticEffect) {
    // Documentation for all available feedback types can be found here:
    // https://developer.apple.com/design/human-interface-guidelines/playing-haptics#Impact
    when (effect) {
      is HapticEffect.None -> {
        /* Nothing to do here. */
      }
      is HapticEffect.ContinuousRubberBanding -> {
        /* Unsupported due to lack of testing. */
      }
      is HapticEffect.Overzoom,
      is HapticEffect.GestureThresholdCrossed -> {
        val impactGenerator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
        impactGenerator.impactOccurred()
      }
    }
  }
}
