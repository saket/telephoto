package me.saket.telephoto.zoomable.internal

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun deviceInfo(): String {
  val context = LocalContext.current
  return remember {
    buildString {
      appendLine("Device = ${Build.MODEL}")
      appendLine("Android version = ${Build.VERSION.SDK_INT}")
      val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
      appendLine("Display resolution = ${Point().also { windowManager.defaultDisplay.getRealSize(it) }}")
      if (Build.VERSION.SDK_INT >= 30) {
        val window = windowManager.currentWindowMetrics
        appendLine("Current window bounds = ${window.bounds}, insets = ${window.windowInsets.getInsets(WindowInsets.Type.systemBars())}")
        if (Build.VERSION.SDK_INT >= 34) {
          appendLine("Current window density = ${window.density}")
        }
      }
    }
  }
}
