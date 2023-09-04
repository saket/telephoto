package me.saket.telephoto.zoomable

import androidx.compose.ui.geometry.Size
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.isSpecified
import kotlin.test.Test

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

    assertThat(ZoomableContentLocation.unscaledAndTopLeftAligned(null).isSpecified).isFalse()
    assertThat(ZoomableContentLocation.unscaledAndTopLeftAligned(Size.Unspecified).isSpecified).isFalse()
    assertThat(ZoomableContentLocation.unscaledAndTopLeftAligned(Size.Zero).isSpecified).isTrue()
    assertThat(ZoomableContentLocation.unscaledAndTopLeftAligned(Size(42f, 99f)).isSpecified).isTrue()
  }
}
