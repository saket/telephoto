package me.saket.telephoto.viewport

import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ZoomableContentLocationTest {
  @Test fun `content size should only be referentially equatable`() {
    val size1 = ZoomableContentLocation.fitInsideAndCenterAligned(Size(10f, 10f))
    val size2 = ZoomableContentLocation.fitInsideAndCenterAligned(Size(10f, 10f))

    // This is needed so that setting a new location object always
    // triggers a position update even if the content size is unchanged
    assertThat(size1).isNotEqualTo(size2)
  }
}
