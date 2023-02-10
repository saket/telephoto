package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

internal fun generateBitmapTileGrid(
  canvasSize: Size,
  unscaledImageSize: Size
): BitmapTileGrid {
  val baseSampleSize = BitmapSampleSize.calculateFor(
    canvasSize = canvasSize,
    scaledImageSize = unscaledImageSize
  )

  // Apart from the base layer, tiles are generated for all possible levels of
  // sample size ahead of time. This will save some allocation during zoom gestures.
  val possibleSampleSizes = generateSequence(seed = baseSampleSize) { previous ->
    if (previous.size == 1) null else previous / 2
  }

  return possibleSampleSizes.associateWith { sampleSize ->
    val tileSize: IntSize = (unscaledImageSize * (sampleSize.size / baseSampleSize.size.toFloat()))
      // TODO: consider smaller tiles with parallel loading of bitmaps with pooled decoders.
      .coerceAtLeast(canvasSize / 2f)
      .discardFractionalParts()

    // Number of tiles can be fractional. To avoid this, the fractional
    // part is discarded and the last tiles on each axis are stretched
    // to cover any remaining space of the image.
    val xTileCount: Int = (unscaledImageSize.width / tileSize.width).toInt()
    val yTileCount: Int = (unscaledImageSize.height / tileSize.height).toInt()

    val tileGrid = ArrayList<BitmapTile>(xTileCount * yTileCount)
    for (x in 0 until xTileCount) {
      for (y in 0 until yTileCount) {
        val isLastXTile = x == xTileCount - 1
        val isLastYTile = y == yTileCount - 1
        val tile = BitmapTile(
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
}

private fun Size.coerceAtLeast(other: Size): Size {
  return Size(
    width = width.coerceAtLeast(other.width),
    height = height.coerceAtLeast(other.height)
  )
}

private fun Size.discardFractionalParts(): IntSize {
  return IntSize(width = width.toInt(), height = height.toInt())
}

/** Calculates a [BitmapSampleSize] for fitting the source image in its viewport's bounds. */
internal fun BitmapSampleSize.Companion.calculateFor(
  canvasSize: Size,
  scaledImageSize: Size
): BitmapSampleSize {
  val zoom = min(
    canvasSize.width / scaledImageSize.width,
    canvasSize.height / scaledImageSize.height
  )
  return calculateFor(zoom)
}

/** Calculates a [BitmapSampleSize] for fitting the source image in its viewport's bounds. */
internal fun BitmapSampleSize.Companion.calculateFor(zoom: Float): BitmapSampleSize {
  var sampleSize = 1
  while (sampleSize * 2 < (1 / zoom)) {
    // BitmapRegionDecoder requires values based on powers of 2.
    sampleSize *= 2
  }
  return BitmapSampleSize(sampleSize)
}

private operator fun BitmapSampleSize.div(other: BitmapSampleSize) = BitmapSampleSize(size / other.size)
private operator fun BitmapSampleSize.div(other: Int) = BitmapSampleSize(size / other)
