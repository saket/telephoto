package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

internal fun generateBitmapTileGrid(
  viewportSize: Size,
  unscaledImageSize: Size
): BitmapTileGrid {
  val baseSampleSize = sampleSizeFor(
    viewportSize = viewportSize,
    scaledImageSize = unscaledImageSize
  )
  val tileGridPerSampleLevel = mutableMapOf<BitmapSampleSize, List<BitmapTile>>()

  var sampleSize = baseSampleSize
  do {
    val tileSize: IntSize = (unscaledImageSize * (sampleSize.size / baseSampleSize.size.toFloat()))
      // TODO: consider smaller tiles with parallel loading of bitmaps with pooled decoders.
      .coerceAtLeast(viewportSize / 2f)
      .discardFractionalParts()

    // Number of tiles can be fractional. To avoid this, the fractional part is removed and the last tiles on each axis are
    val xTileCount: Int = (unscaledImageSize.width / tileSize.width).toInt()
    val yTileCount: Int = (unscaledImageSize.height / tileSize.height).toInt()

    val tileGrid = ArrayList<BitmapTile>(xTileCount * yTileCount)
    for (x in 0 until xTileCount) {
      for (y in 0 until yTileCount) {
        val isLastXTile = x == xTileCount - 1
        val isLastYTile = y == yTileCount - 1
        val tile = BitmapTile(
          sampleSize = sampleSize,
          bounds = IntRect(
            left = x * tileSize.width,
            top = y * tileSize.height,
            right = if (isLastXTile) unscaledImageSize.width.toInt() else (x + 1) * tileSize.width,
            bottom = if (isLastYTile) unscaledImageSize.height.toInt() else (y + 1) * tileSize.height
          )
        )
        tileGrid.add(tile)
      }
    }
    tileGridPerSampleLevel[sampleSize] = tileGrid
    sampleSize /= 2
  } while (sampleSize.size >= 1)

  return tileGridPerSampleLevel
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

internal fun sampleSizeFor(
  viewportSize: Size,
  scaledImageSize: Size
): BitmapSampleSize {
  val zoom = min(
    viewportSize.width / scaledImageSize.width,
    viewportSize.height / scaledImageSize.height
  )

  var sampleSize = 1
  while (sampleSize * 2 < (1 / zoom)) {
    // BitmapRegionDecoder requires values based on powers of 2.
    sampleSize *= 2
  }
  return BitmapSampleSize(sampleSize)
}

internal operator fun BitmapSampleSize.div(other: BitmapSampleSize) = BitmapSampleSize(size / other.size)
internal operator fun BitmapSampleSize.div(other: Int) = BitmapSampleSize(size / other)
