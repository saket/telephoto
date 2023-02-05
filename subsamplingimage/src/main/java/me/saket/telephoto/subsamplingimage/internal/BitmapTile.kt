package me.saket.telephoto.subsamplingimage.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.IntRect

internal data class BitmapTile(
  val bitmap: Bitmap? = null,
  val isLoadingBitmap: Boolean = false,
  val sampleSize: BitmapSampleSize,
  val bounds: IntRect,
)

/** See [BitmapFactory.Options.inSampleSize]. */
@JvmInline
internal value class BitmapSampleSize(val size: Int) {
  init {
    check(size == 1 || size.rem(2) == 0) {
      "Incorrect size = $size. BitmapRegionDecoder requires values based on powers of 2."
    }
  }
}

internal typealias BitmapTileGrid = Map<BitmapSampleSize, List<BitmapTile>>
