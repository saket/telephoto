package me.saket.telephoto.zoomable

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalInspectionMode

@Composable
@ReadOnlyComposable
internal fun isInScreenshotTest(): Boolean {
  // Rely on a couple heuristics to determine if telephoto is running in a screenshot test.
  return LocalInspectionMode.current ||
    "layoutlib" in Build.FINGERPRINT ||
    Build.FINGERPRINT == "robolectric"
}
