package me.saket.telephoto.zoomable

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import org.junit.Rule
import org.junit.Test

class ZoomableImagePreviewTest {
  @get:Rule val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5
  )

  @Test fun `layout preview`() {
    var error: Throwable? = null
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
      // Paparazzi isn't catching errors thrown by composables.
      // TODO: File an issue on https://github.com/cashapp/paparazzi/issues/new/choose
      error = e
    }

    paparazzi.snapshot {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        ZoomableImage(
          modifier = Modifier.fillMaxSize(),
          image = remember {
            object : ZoomableImageSource {
              @Composable override fun resolve(canvasSize: Flow<Size>) =
                ZoomableImageSource.Generic(ColorPainter(Color.Yellow))
            }
          },
          contentDescription = null,
        )
      }
    }
    assertThat(error).isNull()
  }
}
