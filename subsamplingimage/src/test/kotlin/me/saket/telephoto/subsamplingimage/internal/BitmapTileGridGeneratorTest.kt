package me.saket.telephoto.subsamplingimage.internal

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
  @Test fun `correctly generate tile grid`() {
    val imageSize = Size(
      width = 9734f,
      height = 3265f
    )
    val tileGrid = generateBitmapTileGrid(
      canvasSize = Size(
        width = 1080f,
        height = 2214f
      ),
      unscaledImageSize = imageSize
    )

    // Verify that the layers are sorted by their sample size.
    // Base layer with max sampling at the top. Highest quality layer with least sampling at the bottom.
    assertThat(tileGrid.keys.map { it.size }).containsExactly(8, 4, 2, 1).inOrder()

    // Verify that the number of tiles is correct.
    assertThat(
      tileGrid.map { (sample, tiles) -> sample.size to tiles.size }
    ).containsExactly(
      8 to 1,
      4 to 4,
      2 to 8,
      1 to 16,
    )

    tileGrid.forEach { (sampleSize, tiles) ->
      val assert = assertWithMessage("Sample size = ${sampleSize.size}")

      // Verify that the tiles cover the entire image.
      assert.that(tiles.minOf { it.regionBounds.bounds.left }).isEqualTo(0f)
      assert.that(tiles.minOf { it.regionBounds.bounds.top }).isEqualTo(0f)
      assert.that(tiles.maxOf { it.regionBounds.bounds.right }).isEqualTo(imageSize.width)
      assert.that(tiles.maxOf { it.regionBounds.bounds.bottom }).isEqualTo(imageSize.height)
      assert.that(tiles.sumOf { it.regionBounds.bounds.area.toInt() }).isEqualTo(imageSize.area.toInt())

      // Verify that the tiles don't have any overlap.
      val overlappingTiles: List<BitmapTile> = tiles.flatMap { tile ->
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
        generateBitmapTileGrid(
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
    assertThat(time).isLessThan(30.milliseconds)
  }
}

private val Size.area: Float get() = width * height
private val Rect.area: Float get() = size.area
