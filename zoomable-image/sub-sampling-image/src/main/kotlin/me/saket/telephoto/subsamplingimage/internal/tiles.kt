@file:Suppress("DataClassPrivateConstructor")

package me.saket.telephoto.subsamplingimage.internal

import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect

/** Represents a region in the source image that will be drawn in a [ViewportTile]. */
@Immutable
internal data class ImageRegionTile(
  val sampleSize: BitmapSampleSize,
  val bounds: IntRect,
)

/** Represents a region on the Canvas where a [ImageRegionTile] image can be drawn. */
internal data class ViewportTile private constructor(
  val region: ImageRegionTile,
  val bounds: IntRect,
  val isVisible: Boolean,
) {
  constructor(
    region: ImageRegionTile,
    bounds: Rect,
    isVisible: Boolean,
  ) : this(
    region = region,
    // Because the Canvas APIs only accept integer values, any fractional values
    // that arise during tiling must be discarded. However this isn't a problem,
    // since discarding a fractional value will cause the next tile to be shifted
    // back by a pixel and so on, which will eventually eliminate any fractional
    // error. However, this means that the last tiles along the X and Y axes may
    // be one pixel shorter than the image. In practice, this is usually not
    // noticeable to the naked eye, and the benefits of tiling large images outweigh
    // this minor loss of precision.
    bounds = bounds.discardFractionalValues(),
    isVisible = isVisible,
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

/** Collection of [ImageRegionTile] needed for drawing an image at a certain zoom level. */
internal data class ImageRegionTileGrid(
  val base: ImageRegionTile,
  val foreground: Map<BitmapSampleSize, List<ImageRegionTile>>
) {
  companion object; // For extensions.
}
