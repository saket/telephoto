package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
        canvasSize = Size(1080f, 0f),
        unscaledImageSize = Size(10f, 10f)
      )
    }
  }

  @Test fun `image smaller than viewport`() {
    val tileGrid = BitmapRegionTileGrid.generate(
      canvasSize = Size(
        width = 1080f,
        height = 2214f
      ),
      unscaledImageSize = Size(
        width = 500f,
        height = 400f
      )
    )

    assertThat(tileGrid.base.sampleSize).isEqualTo(BitmapSampleSize(1))
    assertThat(tileGrid.foreground).isEmpty()
  }

  @Test fun `image larger than viewport`() {
    val imageSize = Size(
      width = 9734f,
      height = 3265f
    )
    val tileGrid = BitmapRegionTileGrid.generate(
      canvasSize = Size(
        width = 1080f,
        height = 2214f
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

    assertThat(tileGrid.base.regionBounds.bounds).isEqualTo(Rect(Offset.Zero, imageSize))

    tileGrid.foreground.forEach { (sampleSize, tiles) ->
      val assert = assertWithMessage("Sample size = ${sampleSize.size}")

      // Verify that the tiles cover the entire image.
      assert.that(tiles.minOf { it.regionBounds.bounds.left }).isEqualTo(0f)
      assert.that(tiles.minOf { it.regionBounds.bounds.top }).isEqualTo(0f)
      assert.that(tiles.maxOf { it.regionBounds.bounds.right }).isEqualTo(imageSize.width)
      assert.that(tiles.maxOf { it.regionBounds.bounds.bottom }).isEqualTo(imageSize.height)
      assert.that(tiles.sumOf { it.regionBounds.bounds.area.toInt() }).isEqualTo(imageSize.area.toInt())

      // Verify that the tiles don't have any overlap.
      val overlappingTiles: List<BitmapRegionTile> = tiles.flatMap { tile ->
        tiles.minus(tile).filter { other ->
          tile.regionBounds.bounds.overlaps(other.regionBounds.bounds)
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
          canvasSize = Size(
            width = 1080f - (Random.nextInt(0..100)),
            height = 2214f - (Random.nextInt(0..100))
          ),
          unscaledImageSize = Size(
            width = 9734f - (Random.nextInt(0..100)),
            height = 3265f - (Random.nextInt(0..100))
          )
        )
      }
    }

    // The output time is very much machine dependent, so I'm concerned that this test may be flaky.
    println("Generated grids in $time")
    assertThat(time).isLessThan(30.milliseconds)
  }
}

private val Size.area: Float get() = width * height
private val Rect.area: Float get() = size.area
