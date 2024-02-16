package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.unit.IntSize
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class BitmapSampleSizeTest {
  @Test fun `image size smaller than canvas size`() {
    assertThat(
      BitmapSampleSize.calculateFor(
        canvasSize = IntSize(2_000, 1_000),
        scaledImageSize = IntSize(200, 100),
      ).size
    ).isEqualTo(1)
  }

  @Test fun `image size equal to canvas size`() {
    assertThat(
      BitmapSampleSize.calculateFor(
        canvasSize = IntSize(2_000, 1_000),
        scaledImageSize = IntSize(2_000, 1_000),
      ).size
    ).isEqualTo(1)
  }

  @Test fun `image size larger than canvas size`() {
    assertThat(
      BitmapSampleSize.calculateFor(
        canvasSize = IntSize(200, 100),
        scaledImageSize = IntSize(2_000, 1_000),
      ).size
    ).isEqualTo(8)
  }

  @Test fun `throw when canvas size is unavailable`() {
    assertFailure {
      BitmapSampleSize.calculateFor(
        canvasSize = IntSize.Zero,
        scaledImageSize = IntSize(2_000, 1_000),
      )
    }
    assertFailure {
      BitmapSampleSize.calculateFor(
        canvasSize = IntSize(200, 0),
        scaledImageSize = IntSize(2_000, 1_000),
      )
    }
    assertFailure {
      BitmapSampleSize.calculateFor(
        canvasSize = IntSize(0, 200),
        scaledImageSize = IntSize(2_000, 1_000),
      )
    }
  }

  @Test fun `do not throw when image size is unavailable`() {
    assertThat(
      BitmapSampleSize.calculateFor(
        canvasSize = IntSize(200, 100),
        scaledImageSize = IntSize.Zero
      ).size
    ).isEqualTo(1)
  }
}
