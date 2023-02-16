package me.saket.telephoto.subsamplingimage.internal

import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

// todo: doc
internal data class BitmapRegionTile(
  val sampleSize: BitmapSampleSize,
  val bounds: Rect,
)

// todo: doc
@Immutable
internal data class CanvasRegionTile(
  val bitmap: ImageBitmap?,
  val bitmapRegion: BitmapRegionTile,
  val offset: IntOffset,
  val size: IntSize,
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

// todo: doc
internal data class BitmapRegionTileGrid(
  val base: BitmapRegionTile,
  val foreground: Map<BitmapSampleSize, List<BitmapRegionTile>> // TODO: can the key be a ZoomLevel to avoid calculating sample size on every gesture event?
) {
  companion object; // For extensions.
}
