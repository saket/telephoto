package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

internal fun BitmapRegionTileGrid.Companion.generate(
  canvasSize: Size,
  unscaledImageSize: Size
): BitmapRegionTileGrid {
  val baseSampleSize = BitmapSampleSize.calculateFor(
    canvasSize = canvasSize,
    scaledImageSize = unscaledImageSize
  )

  val baseTile = BitmapRegionTile(
    sampleSize = baseSampleSize,
    regionBounds = BitmapRegionBounds(Rect(Offset.Zero, unscaledImageSize))
  )

  // Apart from the base layer, tiles are generated for all possible levels of
  // sample size ahead of time. This will save some allocation during zoom gestures.
  val possibleSampleSizes = generateSequence(seed = baseSampleSize) { current ->
    if (current.size < 2) null else current / 2
  }.drop(1) // Drop base size.

  val foregroundTiles = possibleSampleSizes.associateWith { sampleSize ->
    val tileSize: IntSize = (unscaledImageSize * (sampleSize.size / baseSampleSize.size.toFloat()))
      .coerceAtLeast((canvasSize / 2f).coerceAtMost(unscaledImageSize))
      .discardFractionalParts()

    // Number of tiles can be fractional. To avoid this, the fractional
    // part is discarded and the last tiles on each axis are stretched
    // to cover any remaining space of the image.
    val xTileCount: Int = (unscaledImageSize.width / tileSize.width).toInt()
    val yTileCount: Int = (unscaledImageSize.height / tileSize.height).toInt()

    val tileGrid = ArrayList<BitmapRegionTile>(xTileCount * yTileCount)
    for (x in 0 until xTileCount) {
      for (y in 0 until yTileCount) {
        val isLastXTile = x == xTileCount - 1
        val isLastYTile = y == yTileCount - 1
        val tile = BitmapRegionTile(
          sampleSize = sampleSize,
          regionBounds = BitmapRegionBounds(
            Rect(
              left = x * tileSize.width.toFloat(),
              top = y * tileSize.height.toFloat(),
              // Stretch the last tiles to cover any remaining space.
              right = (if (isLastXTile) unscaledImageSize.width.toInt() else (x + 1) * tileSize.width).toFloat(),
              bottom = (if (isLastYTile) unscaledImageSize.height.toInt() else (y + 1) * tileSize.height).toFloat()
            )
          )
        )
        tileGrid.add(tile)
      }
    }
    return@associateWith tileGrid
  }

  return BitmapRegionTileGrid(
    base = baseTile,
    foreground = foregroundTiles,
  )
}

/** Calculates a [BitmapSampleSize] for fitting the source image in its viewport's bounds. */
internal fun BitmapSampleSize.Companion.calculateFor(
  canvasSize: Size,
  scaledImageSize: Size
): BitmapSampleSize {
  check(canvasSize.minDimension > 0f) { "Can't calculate a sample size for $canvasSize" }

  val zoom = min(
    canvasSize.width / scaledImageSize.width,
    canvasSize.height / scaledImageSize.height
  )
  return calculateFor(zoom)
}

/** Calculates a [BitmapSampleSize] for fitting the source image in its viewport's bounds. */
internal fun BitmapSampleSize.Companion.calculateFor(zoom: Float): BitmapSampleSize {
  check(zoom > 0f) { "Can't calculate a sample size for an image that'll never be shown." }

  var sampleSize = 1
  while (sampleSize * 2 < (1 / zoom)) {
    // BitmapRegionDecoder requires values based on powers of 2.
    sampleSize *= 2
  }
  return BitmapSampleSize(sampleSize)
}

private operator fun BitmapSampleSize.div(other: BitmapSampleSize) = BitmapSampleSize(size / other.size)
private operator fun BitmapSampleSize.div(other: Int) = BitmapSampleSize(size / other)
