package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BitmapSampleSizeTest {
  @Test fun `correctly calculate sample size`() {
    assertThat(
      BitmapSampleSize.calculateFor(
        canvasSize = Size(200f, 100f),
        scaledImageSize = Size.Zero
      ).size
    ).isEqualTo(1)

    assertThat(
      BitmapSampleSize.calculateFor(
        canvasSize = Size(2_000f, 1_000f),
        scaledImageSize = Size(2_000f, 1_000f),
      ).size
    ).isEqualTo(1)

    assertThat(
      BitmapSampleSize.calculateFor(
        canvasSize = Size(200f, 100f),
        scaledImageSize = Size(2_000f, 1_000f),
      ).size
    ).isEqualTo(8)

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
}
