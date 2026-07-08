package me.saket.telephoto.zoomable.coil

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.test.FakeImageLoaderEngine
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoilApi::class)
class ZoomableAsyncImagePreviewTest {
  @get:Rule val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5
  )

  @Test fun `supports fake image loaders`() {
    val imageLoader = ImageLoader.Builder(paparazzi.context)
      .components {
        add(
          FakeImageLoaderEngine.Builder()
            .default(ColorDrawable(0xFF09A88E.toInt()))
            .build()
        )
      }
      .build()

    paparazzi.snapshot {
      ZoomableAsyncImage(
        modifier = Modifier
          .fillMaxSize()
          .wrapContentSize()
          .size(300.dp),
        model = "fake://color-drawable",
        imageLoader = imageLoader,
        contentDescription = null,
      )
    }
  }
}
