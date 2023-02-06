package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

internal fun generateBitmapTileGrid(
  canvasSize: Size,
  unscaledImageSize: Size
): BitmapTileGrid {
  // Calculate the sample size for fitting the image inside its viewport.
  // This will be the base layer. Because it will be fully zoomed out, it
  // does not need to be loaded at full quality and will be down-sampled.
  val baseSampleSize = BitmapSampleSize.calculateFor(
    canvasSize = canvasSize,
    scaledImageSize = unscaledImageSize
  )

  // Apart from the base layer, I'm also generating tiles for all possible levels of
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

    return@associateWith (0 until xTileCount).flatMap { x ->
      (0 until yTileCount).map { y ->
        val isLastXTile = x == xTileCount - 1
        val isLastYTile = y == yTileCount - 1
        BitmapTile(
          sampleSize = sampleSize,
          bounds = Rect(
            left = x * tileSize.width.toFloat(),
            top = y * tileSize.height.toFloat(),
            right = (if (isLastXTile) unscaledImageSize.width.toInt() else (x + 1) * tileSize.width).toFloat(),
            bottom = (if (isLastYTile) unscaledImageSize.height.toInt() else (y + 1) * tileSize.height).toFloat()
          )
        )
      }
    }
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

  var sampleSize = 1
  while (sampleSize * 2 < (1 / zoom)) {
    // BitmapRegionDecoder requires values based on powers of 2.
    sampleSize *= 2
  }
  return BitmapSampleSize(sampleSize)
}

private operator fun BitmapSampleSize.div(other: BitmapSampleSize) = BitmapSampleSize(size / other.size)
private operator fun BitmapSampleSize.div(other: Int) = BitmapSampleSize(size / other)
