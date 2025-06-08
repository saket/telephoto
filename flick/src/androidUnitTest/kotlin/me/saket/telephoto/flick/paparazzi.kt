package me.saket.telephoto.flick

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.Paparazzi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal fun Paparazzi.gif(
  fps: Int = 30,
  end: Duration = 500.milliseconds,
  content: @Composable () -> Unit,
) {
  gif(
    view = ComposeView(context).also { it.setContent(content) },
    fps = fps,
    end = end.inWholeMilliseconds,
  )
}
