package me.saket.telephoto.zoomable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

// TODO: rename this module to :androidUnitTest once https://github.com/cashapp/paparazzi/issues/595 is fixed.
class ZoomablePreviewTest {
  @get:Rule val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5
  )

  @Test fun `layout preview`() {
    paparazzi.snapshot {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color(0xFF333333))
        ) {
          Box(
            Modifier
              .align(Alignment.Center)
              .fillMaxWidth()
              .fillMaxHeight(fraction = 0.4f)
              .padding(16.dp)
              .zoomable(rememberZoomableState())
              .background(
                Brush.linearGradient(listOf(Color.Cyan, Color.Blue)),
                shape = RoundedCornerShape(8.dp)
              )
          )
        }
      }
    }
  }
}
