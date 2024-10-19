package me.saket.telephoto.zoomable

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
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

  @Test fun `content should be rendered on the first frame`() {
    paparazzi.snapshot {
      val painterSize = with(LocalDensity.current) {
        DpSize(100.dp, 120.dp).toSize()
      }
      val painter = remember {
        object : Painter() {
          override val intrinsicSize: Size get() = painterSize
          override fun DrawScope.onDraw() {
            drawRect(
              brush = Brush.linearGradient(
                colors = listOf(
                  Color(0xFF504E9A),
                  Color(0xFF772E6A),
                  Color(0xFF79192C),
                  Color(0xFF560D1A),
                ),
              )
            )
          }
        }
      }

      val zoomableState = rememberZoomableState().also {
        it.contentScale = ContentScale.Fit
        it.contentAlignment = Alignment.BottomEnd
        it.setContentLocation(
          ZoomableContentLocation.scaledInsideAndCenterAligned(painter.intrinsicSize)
        )
      }

      Image(
        modifier = Modifier
          .fillMaxSize()
          .zoomable(zoomableState),
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Inside,
        alignment = Alignment.Center,
      )
    }
  }
}
