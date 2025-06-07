package me.saket.telephoto.zoomable.internal

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal actual fun CompositionLocalConsumerModifierNode.hapticFeedbackPerformer(): HapticFeedbackPerformer {
  return AndroidHapticFeedbackPerformer({ currentValueOf(LocalView) })
}

@Composable
internal actual fun rememberHapticFeedbackPerformer(): HapticFeedbackPerformer {
  val view = LocalView.current
  return remember(view) { AndroidHapticFeedbackPerformer({ view }) }
}

private class AndroidHapticFeedbackPerformer(val view: () -> View) : HapticFeedbackPerformer {
  override suspend fun performHapticFeedback(effect: HapticEffect) {
    when (effect) {
      is HapticEffect.None -> {
        // todo: find an alternative.
        view().performHapticFeedback(HapticFeedbackConstants.NO_HAPTICS)
      }
      is HapticEffect.ContinuousRubberBanding -> {
        if (Build.VERSION.SDK_INT >= 31) {
          val vibrator = (view().context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)!! as VibratorManager)
          try {
            tickerFlow(50.milliseconds)
              .collect {
                val composition = VibrationEffect
                  .startComposition()
                  .also { composition ->
                    val scale = effect.intensityRatio() * 0.005f
                    println("intensity = $scale")
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, scale)
                  }
                  .compose()
                vibrator.defaultVibrator.vibrate(composition)
              }
          } catch (e: CancellationException) {
            println("stopping vibration")
            val composition = VibrationEffect
              .startComposition()
              .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0f)
              .compose()
            vibrator.defaultVibrator.vibrate(composition)
          }
        }
      }
      is HapticEffect.Overzoom -> {
        val constant = when {
          Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.GESTURE_END
          Build.VERSION.SDK_INT >= 23 -> HapticFeedbackConstants.CONTEXT_CLICK // Same effect as GESTURE_END.
          else -> HapticFeedbackConstants.CLOCK_TICK // Same effect as GESTURE_END.
        }
        view().performHapticFeedback(constant)
      }
      is HapticEffect.GestureThresholdCrossed -> {
        view().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
      }
    }
  }
}

private fun tickerFlow(period: Duration, initialDelay: Duration = Duration.ZERO): Flow<Unit> = flow {
  delay(initialDelay)
  while (true) {
    emit(Unit)
    delay(period)
  }
}
