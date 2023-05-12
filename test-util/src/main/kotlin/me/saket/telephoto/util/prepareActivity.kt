package me.saket.telephoto.util

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager

@SuppressLint("NewApi")
fun Activity.prepareForScreenshotTest() {
  actionBar?.hide()
  window.setBackgroundDrawable(ColorDrawable(0xFF1C1A25.toInt()))

  // Remove any space occupied by system bars to reduce differences
  // in from screenshots generated on different devices.
  window.setDecorFitsSystemWindows(false)
  window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
}
