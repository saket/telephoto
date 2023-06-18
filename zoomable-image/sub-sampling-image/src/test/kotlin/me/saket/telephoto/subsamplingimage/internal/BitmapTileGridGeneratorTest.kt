package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Ignore
import org.junit.Test
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@OptIn(ExperimentalTime::class)
class BitmapTileGridGeneratorTest {

  @Test fun `empty canvas size`() {
    assertThrows {
      BitmapRegionTileGrid.generate(
        canvasSize = IntSize(1080, 0),
        unscaledImageSize = IntSize(10, 10),
      )
    }
  }

  @Test fun `image size smaller than layout bounds`() {
    val tileGrid = BitmapRegionTileGrid.generate(
      canvasSize = IntSize(
        width = 1080,
        height = 2214
      ),
      unscaledImageSize = IntSize(
        width = 500,
        height = 400
      )
    )

    assertThat(tileGrid.base.sampleSize).isEqualTo(BitmapSampleSize(1))
    assertThat(tileGrid.foreground).isEmpty()
  }

  @Test fun `image size as a multiplier of layout bounds`() {
    val canvasSize = IntSize(
      width = 1080,
      height = 1920
    )
    val tileGrid = BitmapRegionTileGrid.generate(
      canvasSize = canvasSize,
      unscaledImageSize = canvasSize * 2
    )

    // On telephoto 0.4.0 and lower versions, the sample size for
    // a 4k image displayed in a 1080p layout was calculated as 1.
    assertThat(tileGrid.base.sampleSize).isEqualTo(BitmapSampleSize(2))
    assertThat(tileGrid.foreground).isNotEmpty()
  }

  @Test fun `image size larger than layout bounds`() {
    val imageSize = IntSize(
      width = 9734,
      height = 3265
    )
    val tileGrid = BitmapRegionTileGrid.generate(
      canvasSize = IntSize(
        width = 1080,
        height = 2214
      ),
      unscaledImageSize = imageSize
    )

    // Verify that the layers are sorted by their sample size.
    // Max sampling at the top. Highest quality layer with least sampling at the bottom.
    assertThat(tileGrid.base.sampleSize.size).isEqualTo(8)
    assertThat(tileGrid.foreground.keys.map { it.size }).containsExactly(4, 2, 1).inOrder()

    // Verify that the number of tiles for each sample size is correct.
    assertThat(
      tileGrid.foreground.map { (sample, tiles) -> sample.size to tiles.size }
    ).containsExactly(
      4 to 4,
      2 to 8,
      1 to 16,
    )

    assertThat(tileGrid.base.bounds).isEqualTo(IntRect(IntOffset.Zero, imageSize))

    tileGrid.foreground.forEach { (sampleSize, tiles) ->
      val assert = assertWithMessage("Sample size = ${sampleSize.size}")

      // Verify that the tiles cover the entire image without any gaps.
      assert.that(tiles.minOf { it.bounds.left }).isEqualTo(0)
      assert.that(tiles.minOf { it.bounds.top }).isEqualTo(0)
      assert.that(tiles.maxOf { it.bounds.right }).isEqualTo(imageSize.width)
      assert.that(tiles.maxOf { it.bounds.bottom }).isEqualTo(imageSize.height)
      assert.that(tiles.sumOf { it.bounds.area }).isEqualTo(imageSize.area)

      // Verify that the tiles don't have any overlap.
      val overlappingTiles: List<BitmapRegionTile> = tiles.flatMap { tile ->
        tiles.minus(tile).filter { other ->
          tile.bounds.overlaps(other.bounds)
        }
      }
      assert.that(overlappingTiles).isEmpty()
    }
  }

  @Ignore("The output of this test is different when it's run individually vs with the whole class")
  @Test fun `generation of tiles should be fast enough to be run on the main thread`() {
    val time = measureTime {
      repeat(1_000) {
        BitmapRegionTileGrid.generate(
          canvasSize = IntSize(
            width = 1080 - (Random.nextInt(0..100)),
            height = 2214 - (Random.nextInt(0..100))
          ),
          unscaledImageSize = IntSize(
            width = 9734 - (Random.nextInt(0..100)),
            height = 3265 - (Random.nextInt(0..100))
          )
        )
      }
    }

    // The output time is very much machine dependent, so I'm concerned that this test may be flaky.
    println("Generated grids in $time")
    assertThat(time).isLessThan(30.milliseconds)
  }
}

internal fun BitmapRegionTileGrid.Companion.generate(canvasSize: IntSize, unscaledImageSize: IntSize) =
  BitmapRegionTileGrid.generate(
    canvasSize = canvasSize,
    unscaledImageSize = unscaledImageSize,
    minTileSize = canvasSize / 2
  )

private val IntSize.area: Int get() = width * height
private val IntRect.area: Int get() = size.area
