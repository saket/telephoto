package me.saket.telephoto.util

import android.content.Context
import android.provider.Settings
import com.dropbox.differ.ImageComparator
import com.dropbox.dropshots.ResultValidator
import com.dropbox.dropshots.ThresholdValidator

class CiScreenshotValidator(
  private val context: () -> Context,
  private val tolerancePercentOnLocal: Float,

  /**
   * Overridden by some tests that require a higher
   * tolerance on CI due to machine differences.
   */
  var tolerancePercentOnCi: Float,
) : ResultValidator {

  override fun invoke(result: ImageComparator.ComparisonResult): Boolean {
    val isRunningOnEw = Settings.Global.getString(context().contentResolver, "emulator.wtf") != null
    val isRunningOnCi = System.getenv("CI") != null || isRunningOnEw
    val thresholdPercent = if (isRunningOnCi) tolerancePercentOnCi else tolerancePercentOnLocal
    return ThresholdValidator(threshold = thresholdPercent / 100).invoke(result)
  }
}
