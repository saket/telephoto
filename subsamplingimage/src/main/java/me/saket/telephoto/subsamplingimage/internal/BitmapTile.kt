package me.saket.telephoto.subsamplingimage.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect

internal typealias BitmapTileGrid = Map<BitmapSampleSize, List<BitmapTile>>

@Immutable
internal data class BitmapTile(
  val bitmap: Bitmap? = null, // todo: this does not override equals() :S
  val sampleSize: BitmapSampleSize,

  val regionBounds: BitmapRegionBounds,

  /** [regionBounds] x (scale + pan + rotation). */
  val drawBounds: Rect = Rect.Zero,

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
