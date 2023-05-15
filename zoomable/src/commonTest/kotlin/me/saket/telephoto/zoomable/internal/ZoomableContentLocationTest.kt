package me.saket.telephoto.viewport.internal

import androidx.compose.ui.geometry.Size
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.isSpecified
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZoomableContentLocationTest {
  /**
   * ZoomableImage uses Unspecified as the default value. This ensures that images
   * stay hidden until a location is set and its base-zoom value is calculated.
   */
  @Test fun `when size is null, set location as unspecified`() {
    assertFalse(ZoomableContentLocation.scaledInsideAndCenterAligned(null).isSpecified)
    assertFalse(ZoomableContentLocation.scaledInsideAndCenterAligned(Size.Unspecified).isSpecified)
    assertTrue(ZoomableContentLocation.scaledInsideAndCenterAligned(Size.Zero).isSpecified)
    assertTrue(ZoomableContentLocation.scaledInsideAndCenterAligned(Size(42f, 99f)).isSpecified)

    assertFalse(ZoomableContentLocation.unscaledAndTopStartAligned(null).isSpecified)
    assertFalse(ZoomableContentLocation.unscaledAndTopStartAligned(Size.Unspecified).isSpecified)
    assertTrue(ZoomableContentLocation.unscaledAndTopStartAligned(Size.Zero).isSpecified)
    assertTrue(ZoomableContentLocation.unscaledAndTopStartAligned(Size(42f, 99f)).isSpecified)
  }
}
