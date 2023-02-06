package me.saket.telephoto.subsamplingimage.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Rect

internal data class BitmapTile(
  val bitmap: Bitmap? = null,
  val sampleSize: BitmapSampleSize,
  val bounds: Rect,

  // TODO: when testing is complete and a video has been recorded of
  //  out-of-bound tiles, remove this in favor of filtering out invisible tiles.
  val isVisible: Boolean = true
)

/** See [BitmapFactory.Options.inSampleSize]. */
@JvmInline
internal value class BitmapSampleSize(val size: Int) {
  companion object; // For extensions.

  init {
    check(size == 1 || size.rem(2) == 0) {
      "Incorrect size = $size. BitmapRegionDecoder requires values based on powers of 2."
    }
  }
}

internal typealias BitmapTileGrid = Map<BitmapSampleSize, List<BitmapTile>>
