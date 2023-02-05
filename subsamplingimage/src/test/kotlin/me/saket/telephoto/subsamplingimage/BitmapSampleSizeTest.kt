package me.saket.telephoto.subsamplingimage

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import me.saket.telephoto.subsamplingimage.internal.BitmapSampleSize
import org.junit.Test

class BitmapSampleSizeTest {
  @Test fun `correctly calculate sample size`() {
    assertThat(
      BitmapSampleSize.calculateFor(
        viewportSize = IntSize(2_000, 1_000),
        scaledImageSize = Size(2_000f, 1_000f),
      ).size
    ).isEqualTo(1)

    assertThat(
      BitmapSampleSize.calculateFor(
        viewportSize = IntSize(200, 100),
        scaledImageSize = Size(2_000f, 1_000f),
      ).size
    ).isEqualTo(8)
  }
}
