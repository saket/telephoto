package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.unit.IntSize
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import kotlin.random.Random

class ImageSampleSizeTest {
  @Test fun `image size smaller than canvas size`() {
    assertThat(
      ImageSampleSize.calculateFor(
        viewportSize = IntSize(2_000, 1_000),
        scaledImageSize = IntSize(200, 100),
      ).size
    ).isEqualTo(1)
  }

  @Test fun `image size equal to canvas size`() {
    assertThat(
      ImageSampleSize.calculateFor(
        viewportSize = IntSize(2_000, 1_000),
        scaledImageSize = IntSize(2_000, 1_000),
      ).size
    ).isEqualTo(1)
  }

  @Test fun `image size larger than canvas size`() {
    assertThat(
      ImageSampleSize.calculateFor(
        viewportSize = IntSize(200, 100),
        scaledImageSize = IntSize(2_000, 1_000),
      ).size
    ).isEqualTo(8)
  }

  @Test fun `throw when canvas size is unavailable`() {
    assertFailure {
      ImageSampleSize.calculateFor(
        viewportSize = IntSize.Zero,
        scaledImageSize = IntSize(2_000, 1_000),
      )
    }
    assertFailure {
      ImageSampleSize.calculateFor(
        viewportSize = IntSize(200, 0),
        scaledImageSize = IntSize(2_000, 1_000),
      )
    }
    assertFailure {
      ImageSampleSize.calculateFor(
        viewportSize = IntSize(0, 200),
        scaledImageSize = IntSize(2_000, 1_000),
      )
    }
  }

  @Test fun `do not throw when image size is unavailable`() {
    assertThat(
      ImageSampleSize.calculateFor(
        viewportSize = IntSize(200, 100),
        scaledImageSize = IntSize.Zero
      ).size
    ).isEqualTo(1)
  }

  @Test fun `zero zoom`() {
    assertThat(
      ImageSampleSize.calculateFor(zoom = 0f).size
    ).isEqualTo(1)
  }

  @Test fun `sample size is 1 for large zoom values`() {
    assertThat(
      ImageSampleSize.calculateFor(zoom = Random.nextInt(from = 30, until = 100).toFloat())
    ).isEqualTo(
      ImageSampleSize(1)
    )
  }

  @Test fun `tiny zoom value`() {
    assertThat(
      ImageSampleSize.calculateFor(zoom = 0.001f)
    ).isEqualTo(
      ImageSampleSize(512)
    )
  }

  @Test fun `coerce at most`() {
    val sampleSize = ImageSampleSize.calculateFor(zoom = 0.02651f)
    assertThat(sampleSize).isEqualTo(ImageSampleSize(32))
    assertThat(sampleSize.coerceAtMost(ImageSampleSize(4))).isEqualTo(ImageSampleSize(4))
  }
}
