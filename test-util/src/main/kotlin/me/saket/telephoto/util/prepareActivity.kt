package me.saket.telephoto.util

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.StrictMode
import android.view.WindowManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.test.platform.app.InstrumentationRegistry

fun Activity.prepareForScreenshotTest() {
  if (Build.VERSION.SDK_INT >= 28) {
    // Shouldn't be needed on > API 29, but dropshots is occasionally unable to write to external storage without this.
    val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    uiAutomation.grantRuntimePermission(packageName, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
  }

  try {
    actionBar?.hide()
    window.setBackgroundDrawable(ColorDrawable(0xFF1C1A25.toInt()))
    window.statusBarColor = Color.Transparent.toArgb()

    // Stretch activity to fill the entire display and draw under system bars.
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

  } catch (e: Throwable) {
    println("Failed to prepare activity for screenshot test:")
    e.printStackTrace()
  }

  window.decorView.doOnNextLayout {
    check (Build.VERSION.SDK_INT == 31 && it.width == 1080 && it.height == 2400) {
      "telephoto's test screenshots were generated on an API 31 device with a 1080 x 2400 display/window size."
    }
  }

  StrictMode.setThreadPolicy(
    StrictMode.ThreadPolicy.Builder()
      .detectAll()
      .penaltyDeath()
      .build()
  )
  StrictMode.setVmPolicy(
    StrictMode.VmPolicy.Builder()
      .detectAll()
      .penaltyDeath()
      .build()
  )
}
