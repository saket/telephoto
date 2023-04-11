package me.saket.telephoto.viewport.internal

import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertThat
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.isSpecified
import org.junit.Test

class ZoomableContentLocationTest {
  /**
   * ZoomableImage uses Unspecified as the default value. This ensures that images
   * stay hidden until a location is set and its base-zoom value is calculated.
   */
  @Test fun `when size is null, set location as unspecified`() {
    assertThat(ZoomableContentLocation.scaledInsideAndCenterAligned(null).isSpecified).isFalse()
    assertThat(ZoomableContentLocation.scaledInsideAndCenterAligned(Size.Unspecified).isSpecified).isFalse()
    assertThat(ZoomableContentLocation.scaledInsideAndCenterAligned(Size.Zero).isSpecified).isTrue()
    assertThat(ZoomableContentLocation.scaledInsideAndCenterAligned(Size(42f, 99f)).isSpecified).isTrue()

    assertThat(ZoomableContentLocation.unscaledAndTopStartAligned(null).isSpecified).isFalse()
    assertThat(ZoomableContentLocation.unscaledAndTopStartAligned(Size.Unspecified).isSpecified).isFalse()
    assertThat(ZoomableContentLocation.unscaledAndTopStartAligned(Size.Zero).isSpecified).isTrue()
    assertThat(ZoomableContentLocation.unscaledAndTopStartAligned(Size(42f, 99f)).isSpecified).isTrue()
  }
}
