package me.saket.telephoto.util

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.core.view.WindowCompat

fun Activity.prepareForScreenshotTest() {
  actionBar?.hide()
  window.setBackgroundDrawable(ColorDrawable(0xFF1C1A25.toInt()))

  // Remove any space occupied by system bars to reduce differences
  // in from screenshots generated on different devices.
  WindowCompat.setDecorFitsSystemWindows(window, false)
  window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
}
