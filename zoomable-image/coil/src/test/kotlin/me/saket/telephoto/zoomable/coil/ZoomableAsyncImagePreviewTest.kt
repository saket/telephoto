package me.saket.telephoto.zoomable.coil

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.test.FakeImageLoaderEngine
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
@OptIn(ExperimentalCoilApi::class)
class ZoomableAsyncImagePreviewTest {
  @get:Rule val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5
  )

  private val imageLoader by lazy {
    ImageLoader.Builder(paparazzi.context)
      .components {
        add(
          FakeImageLoaderEngine.Builder()
            .default(ColorDrawable(0xFF09A88E.toInt()))
            .build()
        )
      }
      .build()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test fun `supports fake image loaders`(
    @TestParameter compositionTiming: CompositionTiming,
  ) {
    @Composable
    fun PreviewImage(modifier: Modifier = Modifier) {
      ZoomableAsyncImage(
        modifier = modifier
          .fillMaxSize()
          .wrapContentSize()
          .size(300.dp),
        model = "fake://color-drawable",
        imageLoader = imageLoader,
        contentDescription = null,
      )
    }

    Dispatchers.setMain(Dispatchers.Unconfined)
    try {
      paparazzi.snapshot {
        when (compositionTiming) {
          CompositionTiming.DirectComposition -> PreviewImage()
          CompositionTiming.DelayedComposition -> Scaffold { contentPadding ->
            PreviewImage(Modifier.padding(contentPadding))
          }
        }
      }
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Suppress("unused")
  enum class CompositionTiming {
    DirectComposition,

    /**
     * Must be declared after [DirectComposition] to reproduce
     * [Paparazzi #2382](https://github.com/cashapp/paparazzi/issues/2382).
     */
    DelayedComposition,
  }
}
