package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toRect
import androidx.core.graphics.toRect
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RotationTest {
  @Test fun rotate_entire_image() {
    IntRect(
      topLeft = IntOffset.Zero,
      bottomRight = IntOffset(x = 4032, y = 3024)
    ).let { original ->
      assertThat(original.rotateBy(90, unRotatedParent = original)).isEqualTo(
        IntRect(
          topLeft = IntOffset.Zero,
          bottomRight = IntOffset(x = 3024, y = 4032)
        )
      )
    }

    IntRect(
      topLeft = IntOffset.Zero,
      bottomRight = IntOffset(x = 3024, y = 4032)
    ).let { original ->
      assertThat(original.rotateBy(-90, unRotatedParent = original)).isEqualTo(
        IntRect(
          topLeft = IntOffset.Zero,
          bottomRight = IntOffset(x = 4032, y = 3024)
        )
      )
    }

    IntRect(
      topLeft = IntOffset.Zero,
      bottomRight = IntOffset(x = 3024, y = 4032),
    ).let { original ->
      assertThat(original.rotateBy(-90, original)).isEqualTo(
        IntRect(
          topLeft = IntOffset.Zero,
          bottomRight = IntOffset(x = 4032, y = 3024),
        )
      )
    }

    IntRect(
      topLeft = IntOffset.Zero,
      bottomRight = IntOffset(x = 1600, y = 800)
    ).let { original ->
      assertThat(original.rotateBy(90, original)).isEqualTo(
        IntRect(
          topLeft = IntOffset.Zero,
          bottomRight = IntOffset(x = 800, y = 1600)
        )
      )
    }
  }

  @Test fun rotate_parts_of_an_image() {
    val landscape = IntRect(
      topLeft = IntOffset.Zero,
      bottomRight = IntOffset(x = 1600, y = 800)
    )

    assertThat(
      IntRect(
        topLeft = landscape.topCenter,
        bottomRight = landscape.centerRight,
      ).rotateBy(90, landscape)
    ).isEqualTo(
      IntRect(
        topLeft = IntOffset(x = 400, y = 800),
        bottomRight = IntOffset(x = 800, y = 1600),
      )
    )
    assertThat(
      IntRect(
        topLeft = landscape.topCenter,
        bottomRight = IntOffset(x = 1200, y = 400),
      ).rotateBy(180, landscape)
    ).isEqualTo(
      IntRect(
        topLeft = IntOffset(x = 400, y = 400),
        bottomRight = IntOffset(x = 800, y = 800),
      )
    )
    assertThat(
      IntRect(
        offset = IntOffset(x = 400, y = 400),
        size = IntSize(400, 400)
      ).rotateBy(270, landscape)
    ).isEqualTo(
      IntRect(
        topLeft = IntOffset(x = 400, y = 800),
        bottomRight = IntOffset(x = 800, y = 1200),
      )
    )
    assertThat(
      IntRect(
        topLeft = landscape.topCenter,
        bottomRight = landscape.centerRight,
      )
        .rotateBy(90, landscape)
        .rotateBy(90, landscape)
        .rotateBy(90, landscape)
        .rotateBy(90, landscape)
    ).isEqualTo(
      IntRect(
        topLeft = landscape.topCenter,
        bottomRight = landscape.centerRight,
      )
    )

    val portrait = IntRect(
      topLeft = IntOffset.Zero,
      bottomRight = IntOffset(x = 800, y = 1600)
    )
    val bottomLeftRect = IntRect(
      topLeft = portrait.centerLeft,
      bottomRight = portrait.bottomCenter,
    )
    assertThat(bottomLeftRect.rotateBy(-90, portrait)).isEqualTo(
      IntRect(
        topLeft = IntOffset(x = 800, y = 400),
        bottomRight = IntOffset(x = 1600, y = 800)
      )
    )

    IntRect(
      topLeft = IntOffset.Zero,
      bottomRight = IntOffset(x = 5, y = 6)
    ).let { parent ->
      val rect = IntRect(
        topLeft = IntOffset(1, 2),
        bottomRight = IntOffset(2, 4),
      )

      assertThat(rect.rotateBy(-90, parent)).isEqualTo(
        IntRect(
          topLeft = IntOffset(2, 3),
          bottomRight = IntOffset(4, 4),
        )
      )
    }
  }

  @Test fun `rotation matrix for filling viewport bounds when exif orientation is 90 degrees`() {
    val imageBounds = Rect(Offset.Zero, Size(1328f, 1000f))
    val matrix = createRotationMatrix(
      bitmapSize = imageBounds.size,
      orientation = ExifMetadata.ImageOrientation.Orientation90,
      bounds = Size(1080f, 2400f),
    )

    assertThat(matrix.mapRect(imageBounds)).isEqualTo(
      Rect(0f, 0f, 1080f, 2400f)
    )
  }

  @Test fun `rotation matrix for filling viewport bounds when exif orientation is none`() {
    val imageBounds = Rect(Offset.Zero, Size(1000f, 1328f))
    val matrix = createRotationMatrix(
      bitmapSize = imageBounds.size,
      orientation = ExifMetadata.ImageOrientation.None,
      bounds = Size(1080f, 2400f),
    )

    assertThat(matrix.mapRect(imageBounds)).isEqualTo(
      Rect(0f, 0f, 1080f, 2400f)
    )
  }

  @Test fun `rotation matrix for upscaling an image`() {
    val imageBounds = Rect(Offset.Zero, Size(250f, 167f))
    val matrix = createRotationMatrix(
      bitmapSize = imageBounds.size,
      orientation = ExifMetadata.ImageOrientation.None,
      bounds = Size(1080f, 2400f),
    )

    assertThat(matrix.mapRect(imageBounds)).isEqualTo(
      Rect(0f, 0f, 1080f, 2400f)
    )
  }

  @Test fun `rotation matrix maintains pixel perfect alignment`() {
    // Test with various image sizes that aren't multiples of the viewport.
    val viewportBounds = Size(1080f, 2400f)
    val imageSizes = listOf(
      Size(250f, 167f),
      Size(333f, 555f),    // Odd dimensions
      Size(1000f, 1328f),  // Normal image with 0° rotation
      Size(1328f, 1000f),  // Normal image with 90° rotation
    )

    for (imageSize in imageSizes) {
      for (orientation in ExifMetadata.ImageOrientation.entries) {
        val imageBounds = Rect(Offset.Zero, imageSize)
        val matrix = createRotationMatrix(
          bitmapSize = imageSize,
          orientation = orientation,
          bounds = viewportBounds
        )

        val mappedRect = matrix.mapRect(imageBounds)
        try {
          assertThat(mappedRect).isEqualTo(
            Rect(0f, 0f, viewportBounds.width, viewportBounds.height)
          )
        } catch (e: Throwable) {
          println("Failed for $imageSize at $orientation")
          throw e
        }
      }
    }
  }

  @Test fun `rotation matrix ensures no gaps between tiles`() {
    val viewportBounds = Size(1080f, 2400f)
    val imageSize = Size(2700f, 6000f)

    val tiles = ImageRegionTileGrid.generate(
      viewportSize = viewportBounds.discardFractionalParts(),
      unscaledImageSize = imageSize.discardFractionalParts(),
    ).foreground.values.single()

    val matrix = createRotationMatrix(
      bitmapSize = imageSize,
      orientation = ExifMetadata.ImageOrientation.None,
      bounds = viewportBounds
    )

    val mappedTiles = tiles.map { tile ->
      matrix.mapRect(tile.bounds.toRect())
    }

    // Verify no gaps.
    val totalArea = mappedTiles.sumOf { (it.width * it.height).toDouble() }.toFloat()
    val expectedArea = viewportBounds.width * viewportBounds.height
    assertThat(totalArea).isEqualTo(expectedArea)
  }

  private fun android.graphics.Matrix.mapRect(rect: Rect): Rect {
    val rectF = rect.toAndroidRectF()
    mapRect(rectF)
    return rectF.toRect().toComposeRect()
  }
}
