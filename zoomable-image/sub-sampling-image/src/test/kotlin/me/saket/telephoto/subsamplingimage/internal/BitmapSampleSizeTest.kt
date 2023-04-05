package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BitmapSampleSizeTest {
  @Test fun `image size smaller than canvas size`() {
    assertThat(
      BitmapSampleSize.calculateFor(
        canvasSize = Size(2_000f, 1_000f),
        scaledImageSize = Size(200f, 100f),
      ).size
    ).isEqualTo(1)
  }

  @Test fun `image size equal to canvas size`() {
    assertThat(
      BitmapSampleSize.calculateFor(
        canvasSize = Size(2_000f, 1_000f),
        scaledImageSize = Size(2_000f, 1_000f),
      ).size
    ).isEqualTo(1)
  }

  @Test fun `image size larger than canvas size`() {
    assertThat(
      BitmapSampleSize.calculateFor(
        canvasSize = Size(200f, 100f),
        scaledImageSize = Size(2_000f, 1_000f),
      ).size
    ).isEqualTo(8)
  }

  @Test fun `throw when canvas size is unavailable`() {
    assertThrows {
      BitmapSampleSize.calculateFor(
        canvasSize = Size.Zero,
        scaledImageSize = Size(2_000f, 1_000f),
      )
    }
    assertThrows {
      BitmapSampleSize.calculateFor(
        canvasSize = Size(200f, 0f),
        scaledImageSize = Size(2_000f, 1_000f),
      )
    }
    assertThrows {
      BitmapSampleSize.calculateFor(
        canvasSize = Size(0f, 200f),
        scaledImageSize = Size(2_000f, 1_000f),
      )
    }
  }

  @Test fun `do not throw when image size is unavailable`() {
    assertThat(
      BitmapSampleSize.calculateFor(
        canvasSize = Size(200f, 100f),
        scaledImageSize = Size.Zero
      ).size
    ).isEqualTo(1)
  }
}
