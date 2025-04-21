package me.saket.telephoto.util

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnNextLayout

class ScreenshotTestActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
    window.setBackgroundDrawable(ColorDrawable(0xFF1C1A25.toInt()))
    window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    window.decorView.doOnNextLayout {
      val isExpectedSize = when (val orientation = resources.configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> it.width == 1080 && it.height == 2400
        Configuration.ORIENTATION_LANDSCAPE -> it.width == 2400 && it.height == 1080
        else -> error("invalid orientation = $orientation")
      }
      check(Build.VERSION.SDK_INT == 34 && isExpectedSize) {
        "Telephoto's test screenshots were generated on an API 34 device with a 1080 x 2400 display/window size." +
          "Current device: API ${Build.VERSION.SDK_INT}, window size = ${it.width} x ${it.height}"
      }
    }
  }
}
