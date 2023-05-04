package me.saket.telephoto.subsamplingimage.internal

import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect

/** Represents a region in an image that whose bitmap needs to be loaded. */
internal data class BitmapRegionTile(
  val sampleSize: BitmapSampleSize,
  val bounds: IntRect,
)

/** Represents a region on a Canvas where [bitmap] can be drawn. */
@Immutable
internal data class CanvasRegionTile(
  val bitmap: ImageBitmap?,
  val bitmapRegion: BitmapRegionTile,
  val bounds: IntRect,
  val isBaseTile: Boolean,
) {
  constructor(
    bitmap: ImageBitmap?,
    bitmapRegion: BitmapRegionTile,
    bounds: Rect,
    isBaseTile: Boolean,
  ) : this(
    bitmap = bitmap,
    bitmapRegion = bitmapRegion,
    // Because the Canvas APIs only accept integer values, any fractional values
    // that arise during tiling must be discarded. However this isn't a problem,
    // since discarding a fractional value will cause the next tile to be shifted
    // back by a pixel and so on, which will eventually eliminate any fractional
    // error. However, this means that the last tiles along the X and Y axes may
    // be one pixel shorter than the image. In practice, this is usually not
    // noticeable to the naked eye, and the benefits of tiling large images outweigh
    // this minor loss of precision.
    bounds = bounds.discardFractionalValues(),
    isBaseTile = isBaseTile,
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

/** Collection of [BitmapRegionTile] needed for drawing an image at a certain zoom level. */
internal data class BitmapRegionTileGrid(
  val base: BitmapRegionTile,
  val foreground: Map<BitmapSampleSize, List<BitmapRegionTile>>
) {
  companion object; // For extensions.
}
