package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize

internal fun BitmapRegionTileGrid.Companion.generate(
  canvasSize: IntSize,
  unscaledImageSize: IntSize,
  minTileSize: IntSize,
): BitmapRegionTileGrid {
  val baseSampleSize = BitmapSampleSize.calculateFor(
    canvasSize = canvasSize,
    scaledImageSize = unscaledImageSize
  )

  val baseTile = BitmapRegionTile(
    sampleSize = baseSampleSize,
    bounds = IntRect(IntOffset.Zero, unscaledImageSize)
  )

  // Apart from the base layer, tiles are generated for all possible levels of
  // sample size ahead of time. This will save some allocation during zoom gestures.
  val possibleSampleSizes = generateSequence(seed = baseSampleSize) { current ->
    if (current.size < 2) null else current / 2
  }.drop(1) // Skip base size.

  val foregroundTiles = possibleSampleSizes.associateWith { sampleSize ->
    val tileSize: IntSize = (unscaledImageSize.toSize() * (sampleSize.size / baseSampleSize.size.toFloat()))
      .discardFractionalParts()
      .coerceIn(min = minTileSize, max = unscaledImageSize.coerceAtLeast(minTileSize))

    // Number of tiles can be fractional. To avoid this, the fractional
    // part is discarded and the last tiles on each axis are stretched
    // to cover any remaining space of the image.
    val xTileCount: Int = unscaledImageSize.width / tileSize.width
    val yTileCount: Int = unscaledImageSize.height / tileSize.height

    val tileGrid = ArrayList<BitmapRegionTile>(xTileCount * yTileCount)
    for (x in 0 until xTileCount) {
      for (y in 0 until yTileCount) {
        val isLastXTile = x == xTileCount - 1
        val isLastYTile = y == yTileCount - 1
        val tile = BitmapRegionTile(
          sampleSize = sampleSize,
          bounds = IntRect(
            left = x * tileSize.width,
            top = y * tileSize.height,
            // Stretch the last tiles to cover any remaining space.
            right = if (isLastXTile) unscaledImageSize.width else (x + 1) * tileSize.width,
            bottom = if (isLastYTile) unscaledImageSize.height else (y + 1) * tileSize.height,
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

/** Calculates a [BitmapSampleSize] for fitting a source image in its layout bounds. */
internal fun BitmapSampleSize.Companion.calculateFor(
  canvasSize: IntSize,
  scaledImageSize: IntSize
): BitmapSampleSize {
  check(canvasSize.minDimension > 0f) { "Can't calculate a sample size for $canvasSize" }

  val zoom = minOf(
    canvasSize.width / scaledImageSize.width.toFloat(),
    canvasSize.height / scaledImageSize.height.toFloat()
  )
  return calculateFor(zoom)
}

/** Calculates a [BitmapSampleSize] for fitting a source image in its layout bounds. */
internal fun BitmapSampleSize.Companion.calculateFor(zoom: Float): BitmapSampleSize {
  if (zoom == 0f) {
    return BitmapSampleSize(1)
  }

  var sampleSize = 1
  while (sampleSize * 2 <= (1 / zoom)) {
    // BitmapRegionDecoder requires values based on powers of 2.
    sampleSize *= 2
  }
  return BitmapSampleSize(sampleSize)
}

private operator fun BitmapSampleSize.div(other: BitmapSampleSize) = BitmapSampleSize(size / other.size)
private operator fun BitmapSampleSize.div(other: Int) = BitmapSampleSize(size / other)
