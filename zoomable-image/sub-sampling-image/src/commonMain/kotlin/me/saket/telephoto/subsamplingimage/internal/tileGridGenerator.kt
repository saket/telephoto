package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize

internal fun ImageRegionTileGrid.Companion.generate(
  viewportSize: IntSize,
  unscaledImageSize: IntSize,
  minTileSize: IntSize = viewportSize / 2,
): ImageRegionTileGrid {
  val baseSampleSize = ImageSampleSize.calculateFor(
    viewportSize = viewportSize,
    scaledImageSize = unscaledImageSize
  )

  val baseTile = ImageRegionTile(
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
    val xTileCount: Int = (unscaledImageSize.width / tileSize.width).coerceAtLeast(1)
    val yTileCount: Int = (unscaledImageSize.height / tileSize.height).coerceAtLeast(1)

    val tileGrid = ArrayList<ImageRegionTile>(xTileCount * yTileCount)
    for (x in 0 until xTileCount) {
      for (y in 0 until yTileCount) {
        val isLastXTile = x == xTileCount - 1
        val isLastYTile = y == yTileCount - 1
        val tile = ImageRegionTile(
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

  return ImageRegionTileGrid(
    base = baseTile,
    foreground = foregroundTiles,
  )
}

/** Calculates a [ImageSampleSize] for fitting a source image in its layout bounds. */
internal fun ImageSampleSize.Companion.calculateFor(
  viewportSize: IntSize,
  scaledImageSize: IntSize
): ImageSampleSize {
  check(viewportSize.minDimension > 0f) { "Can't calculate a sample size for $viewportSize" }

  val zoom = minOf(
    viewportSize.width / scaledImageSize.width.toFloat(),
    viewportSize.height / scaledImageSize.height.toFloat()
  )
  return calculateFor(zoom)
}

/** Calculates a [ImageSampleSize] for fitting a source image in its layout bounds. */
internal fun ImageSampleSize.Companion.calculateFor(zoom: Float): ImageSampleSize {
  if (zoom == 0f) {
    return ImageSampleSize(1)
  }

  var sampleSize = 1
  while (sampleSize * 2 <= (1 / zoom)) {
    // BitmapRegionDecoder requires values based on powers of 2.
    sampleSize *= 2
  }
  return ImageSampleSize(sampleSize)
}

private operator fun ImageSampleSize.div(other: ImageSampleSize) = ImageSampleSize(size / other.size)
private operator fun ImageSampleSize.div(other: Int) = ImageSampleSize(size / other)
