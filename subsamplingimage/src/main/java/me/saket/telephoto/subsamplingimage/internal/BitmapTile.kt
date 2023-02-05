package me.saket.telephoto.subsamplingimage.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

internal data class BitmapTile(
  val bitmap: Bitmap?,
  val isLoadingBitmap: Boolean,
  val sampleSize: BitmapSampleSize,
  val bounds: IntRect,
)

/** See [BitmapFactory.Options.inSampleSize]. */
@JvmInline
internal value class BitmapSampleSize(
  val size: Int
) {

  operator fun div(other: BitmapSampleSize) = BitmapSampleSize(size / other.size)
  operator fun div(other: Int) = BitmapSampleSize(size / other)

  companion object {
    internal fun calculateFor(
      viewportSize: IntSize,
      scaledImageSize: Size
    ): BitmapSampleSize {
      val zoom = min(
        viewportSize.width / scaledImageSize.width,
        viewportSize.height / scaledImageSize.height
      )

      var sampleSize = 1
      while (sampleSize * 2 < (1 / zoom)) {
        // BitmapRegionDecoder requires values based on powers of 2.
        sampleSize *= 2
      }
      return BitmapSampleSize(sampleSize)
    }
  }
}
