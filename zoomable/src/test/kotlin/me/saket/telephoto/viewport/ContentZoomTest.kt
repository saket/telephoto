package me.saket.telephoto.viewport

import androidx.compose.ui.layout.ScaleFactor
import com.google.common.truth.Truth.assertThat
import me.saket.telephoto.zoomable.ContentZoom
import me.saket.telephoto.zoomable.ZoomRange
import org.junit.Test

class ContentZoomTest {
  @Test fun `coerce between a range`() {
    // Scenario: content is at its min zoom.
    assertThat(
      ContentZoom(
        baseZoom = ScaleFactor(4f, 4f),
        userZoom = 1f
      ).coercedIn(
        ZoomRange(
          minZoomAsRatioOfBaseZoom = 1f,
          maxZoomAsRatioOfSize = 1f
        )
      )
    ).isEqualTo(
      ContentZoom(
        baseZoom = ScaleFactor(4f, 4f),
        userZoom = 1f
      )
    )

    // Scenario: content is under-zoomed.
    assertThat(
      ContentZoom(
        baseZoom = ScaleFactor(4f, 4f),
        userZoom = 0.8f
      ).coercedIn(
        ZoomRange(
          minZoomAsRatioOfBaseZoom = 1f,
          maxZoomAsRatioOfSize = 1f
        )
      )
    ).isEqualTo(
      ContentZoom(
        baseZoom = ScaleFactor(4f, 4f),
        userZoom = 1f
      )
    )

    // Scenario: content is under-zoomed, but is still under its allowed leeway.
    assertThat(
      ContentZoom(
        baseZoom = ScaleFactor(4f, 4f),
        userZoom = 0.8f
      ).coercedIn(
        range = ZoomRange(
          minZoomAsRatioOfBaseZoom = 1f,
          maxZoomAsRatioOfSize = 1f
        ),
        leewayPercentForMinZoom = 0.2f,
      )
    ).isEqualTo(
      ContentZoom(
        baseZoom = ScaleFactor(4f, 4f),
        userZoom = 0.8f
      )
    )

    // Scenario: content is under-zoomed beyond its allowed leeway.
    assertThat(
      ContentZoom(
        baseZoom = ScaleFactor(4f, 4f),
        userZoom = 0.5f
      ).coercedIn(
        range = ZoomRange(
          minZoomAsRatioOfBaseZoom = 1f,
          maxZoomAsRatioOfSize = 1f
        ),
        leewayPercentForMinZoom = 0.2f,
      )
    ).isEqualTo(
      ContentZoom(
        baseZoom = ScaleFactor(4f, 4f),
        userZoom = 0.8f
      )
    )
  }
}
