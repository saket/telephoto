package me.saket.telephoto.zoomable

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.random.Random
import kotlin.test.Test

class ZoomableContentLocationTest {
  /**
   * ZoomableImage uses Unspecified as the default value. This ensures that images
   * stay hidden until a location is set and its base-zoom value is calculated.
   */
  @Test fun `when size is null, set location as unspecified`() {
    assertThat(ZoomableContentLocation.scaledInsideAndCenterAligned(null).isSpecified).isFalse()
    assertThat(ZoomableContentLocation.scaledInsideAndCenterAligned(Size.Unspecified).isSpecified).isTrue()
    assertThat(ZoomableContentLocation.scaledInsideAndCenterAligned(Size.Zero).isSpecified).isTrue()
    assertThat(ZoomableContentLocation.scaledInsideAndCenterAligned(Size(42f, 99f)).isSpecified).isTrue()

    assertThat(ZoomableContentLocation.unscaledAndTopLeftAligned(null).isSpecified).isFalse()
    assertThat(ZoomableContentLocation.unscaledAndTopLeftAligned(Size.Unspecified).isSpecified).isTrue()
    assertThat(ZoomableContentLocation.unscaledAndTopLeftAligned(Size.Zero).isSpecified).isTrue()
    assertThat(ZoomableContentLocation.unscaledAndTopLeftAligned(Size(42f, 99f)).isSpecified).isTrue()

    assertThat(ZoomableContentLocation.scaledToFitAndCenterAligned(null).isSpecified).isFalse()
    assertThat(ZoomableContentLocation.scaledToFitAndCenterAligned(Size.Unspecified).isSpecified).isTrue()
    assertThat(ZoomableContentLocation.scaledToFitAndCenterAligned(Size.Zero).isSpecified).isTrue()
    assertThat(ZoomableContentLocation.scaledToFitAndCenterAligned(Size(42f, 99f)).isSpecified).isTrue()
  }

  private val ZoomableContentLocation.isSpecified: Boolean
    get() {
      val layoutSize = Size(width = Random.nextInt(0, 1000).toFloat(), height = Random.nextInt(0, 1000).toFloat())
      return this != ZoomableContentLocation.Unspecified && location(layoutSize, LayoutDirection.Ltr).size.isSpecified
    }
}
