package me.saket.telephoto.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.children
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.screenshot.Screenshot

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

    CorrectDisplayHeightLayout.wrapDecorViewContent(this)

  } catch (e: Throwable) {
    println("Failed to prepare activity for screenshot test:")
    e.printStackTrace()
  }
}

/** Workaround for [u/283219177](https://issuetracker.google.com/issues/283219177). */
fun Activity.screenshotForMinSdk23(): Bitmap {
  val contentView = findViewById<View>(CorrectDisplayHeightLayout.ID)
  return Screenshot.capture(contentView).bitmap
}

/** Workaround for [u/283219177](https://issuetracker.google.com/issues/283219177). */
@SuppressLint("ViewConstructor")
private class CorrectDisplayHeightLayout(
  context: Context,
  private val fixedHeight: Int
) : FrameLayout(context) {

  companion object {
    val ID = View.generateViewId()

    fun wrapDecorViewContent(activity: Activity) {
      if (Build.VERSION.SDK_INT >= 24) {
        val minSdk = activity.packageManager.getApplicationInfo(activity.packageName, 0).minSdkVersion
        check(minSdk <= 23) { "this hack is not required if minSdk > 23" }
      }

      val realDisplayHeight = Point().also {
        @Suppress("DEPRECATION")  // The alternative does not report the correct display size.
        activity.windowManager.defaultDisplay.getRealSize(it)
      }.y

      val decorView = activity.window.decorView.rootView as ViewGroup
      val newDecorRoot = CorrectDisplayHeightLayout(activity, fixedHeight = realDisplayHeight).apply {
        id = ID
        background = decorView.background
        decorView.moveAllChildrenTo(this)
      }
      decorView.addView(newDecorRoot)
    }

    private fun ViewGroup.moveAllChildrenTo(other: ViewGroup) {
      val children = children.toList()
      removeAllViews()
      children.forEach(other::addView)
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(fixedHeight, MeasureSpec.EXACTLY))
  }
}
