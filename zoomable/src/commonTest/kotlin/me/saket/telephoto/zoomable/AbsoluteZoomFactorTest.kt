package me.saket.telephoto.zoomable

import androidx.compose.ui.layout.ScaleFactor
import assertk.assertThat
import assertk.assertions.isCloseTo
import kotlin.test.Test

class AbsoluteZoomFactorTest {
  @Test fun `calculate final zoom`() {
    val targetZoom = AbsoluteZoomFactor.forFinalZoom(
      baseZoom = BaseZoomFactor(ScaleFactor(1.1f, 1.8f)),
      finalZoom = 2.5f,
    )
    assertThat(targetZoom.userZoom.value).isCloseTo(1.38f, delta = 0.01f)
  }
}
