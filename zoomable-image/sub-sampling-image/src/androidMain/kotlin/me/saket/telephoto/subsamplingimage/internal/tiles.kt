@file:Suppress("DataClassPrivateConstructor")

package me.saket.telephoto.subsamplingimage.internal

import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntRect

/** A region in the source image that will be drawn in a [ViewportTile]. */
@Immutable
internal data class ImageRegionTile(
  val sampleSize: ImageSampleSize,
  val bounds: IntRect,
)

/** A region in the viewport/canvas where a [ImageRegionTile] image will be drawn. */
internal data class ViewportTile private constructor(
  val region: ImageRegionTile,
  val bounds: IntRect,
  val isVisible: Boolean,
  val isBase: Boolean,
) {
  constructor(
    region: ImageRegionTile,
    bounds: Rect,
    isVisible: Boolean,
    isBase: Boolean,
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
    isBase = isBase,
  )
}

/**
 * This is kept separate from [ViewportTile] to optimize the drawing of the base tile.
 * The base tile may be present in the viewport but can be skipped during rendering if
 * it's entirely covered by foreground tiles.
 */
@Immutable
internal data class ViewportImageTile(
  private val tile: ViewportTile,
  val painter: Painter?,
) {
  val bounds get() = tile.bounds
  val isBase get() = tile.isBase
}

/** See [BitmapFactory.Options.inSampleSize]. */
@JvmInline
internal value class ImageSampleSize(val size: Int) {
  companion object; // For extensions.

  init {
    check(size == 1 || size.rem(2) == 0) {
      "Incorrect size = $size. BitmapRegionDecoder requires values based on powers of 2."
    }
  }

  fun coerceAtMost(other: ImageSampleSize): ImageSampleSize {
    return if (size > other.size) other else this
  }
}

/** Collection of [ImageRegionTile] needed for drawing an image at a certain zoom level. */
internal data class ImageRegionTileGrid(
  val base: ImageRegionTile,
  val foreground: Map<ImageSampleSize, List<ImageRegionTile>>
) {
  companion object; // For extensions.
}
