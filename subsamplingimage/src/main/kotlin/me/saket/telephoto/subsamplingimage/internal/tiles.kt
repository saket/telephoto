package me.saket.telephoto.subsamplingimage.internal

import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect

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
  val bounds: IntRect,
) {
  constructor(
    bitmap: ImageBitmap?,
    bitmapRegion: BitmapRegionTile,
    bounds: Rect,
  ) : this(
    bitmap = bitmap,
    bitmapRegion = bitmapRegion,
    // Canvas only accepts integers so fractional values must be discarded.
    // This is okay because if any fractional part was present and discarded,
    // the next tile will also move back by a pixel. This would cause the last
    // tiles on X and Y axes to be 1px short, but that's unnoticeable to eyes.
    bounds = bounds.discardFractionalValues(),
  )
}

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
