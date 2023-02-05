package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import me.saket.telephoto.subsamplingimage.internal.BitmapSampleSize
import me.saket.telephoto.subsamplingimage.internal.sampleSizeFor
import org.junit.Test

class BitmapSampleSizeTest {
  @Test fun `correctly calculate sample size`() {
    assertThat(
      sampleSizeFor(
        viewportSize = Size(2_000f, 1_000f),
        scaledImageSize = Size(2_000f, 1_000f),
      ).size
    ).isEqualTo(1)

    assertThat(
      sampleSizeFor(
        viewportSize = Size(200f, 100f),
        scaledImageSize = Size(2_000f, 1_000f),
      ).size
    ).isEqualTo(8)
  }
}
