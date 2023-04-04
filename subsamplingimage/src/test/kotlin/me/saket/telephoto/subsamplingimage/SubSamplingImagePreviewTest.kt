package me.saket.telephoto.subsamplingimage

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import me.saket.telephoto.zoomable.rememberZoomableState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class SubSamplingImagePreviewTest {
  @get:Rule val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5
  )

  @Test fun `layout preview does not crash`(@TestParameter source: ImageSourceParam) {
    var error: Throwable? = null
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
      // Paparazzi isn't catching errors thrown by composables.
      // TODO: File an issue on https://github.com/cashapp/paparazzi/issues/new/choose
      error = e
    }

    paparazzi.snapshot {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        val state = rememberSubSamplingImageState(
          zoomableState = rememberZoomableState(),
          imageSource = source.source
        )
        SubSamplingImage(
          state = state,
          contentDescription = null
        )
      }
    }

    assertThat(error).isNull()
  }

  @Suppress("unused")
  enum class ImageSourceParam(val source: ImageSource) {
    NonExistentImage(ImageSource.asset("asset_that_does_not_exist.jpg")),
    ExistentImage(ImageSource.asset("pixel.jpg")),
  }
}
