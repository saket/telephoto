package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class BitmapTileGridTest {
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
      assert.that(tiles.minOf { it.bounds.left }).isEqualTo(0f)
      assert.that(tiles.minOf { it.bounds.top }).isEqualTo(0f)
      assert.that(tiles.maxOf { it.bounds.right }).isEqualTo(imageSize.width)
      assert.that(tiles.maxOf { it.bounds.bottom }).isEqualTo(imageSize.height)

      // Verify that the tiles don't have any overlap.
      val overlappingTiles: List<BitmapTile> = tiles.flatMap { tile ->
        tiles.minus(tile).filter { other ->
          tile.bounds.overlaps(other.bounds)
        }
      }
      assert.that(overlappingTiles).isEmpty()
    }
  }
}
