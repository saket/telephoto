package me.saket.telephoto.subsampling

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntRect
import com.dropbox.dropshots.Dropshots
import com.dropbox.dropshots.ResultValidator
import com.dropbox.dropshots.ThresholdValidator
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.internal.CanvasRegionTile
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.viewport.ZoomableContentTransformation
import me.saket.telephoto.viewport.ZoomableViewport
import me.saket.telephoto.viewport.rememberZoomableViewportState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class SubSamplingImageTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val dropshots = Dropshots(
    filenameFunc = { it.replace(" ", "_") },
    resultValidator = ThresholdValidator(thresholdPercent = 0.02f)
  )

  @Before
  fun setup() {
    composeTestRule.activityRule.scenario.onActivity {
      it.actionBar?.hide()

      // Remove any space occupied by system bars to reduce differences
      // in from screenshots generated on different devices.
      it.window.setDecorFitsSystemWindows(false)
      it.window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }
  }

  @Test fun canary() {
    var isImageDisplayed = false

    composeTestRule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableViewportState()
        val imageState = rememberSubSamplingImageState(
          viewportState = viewportState,
          imageSource = ImageSource.asset("pahade.jpg"),
        )
        LaunchedEffect(imageState.isImageDisplayed) {
          isImageDisplayed = imageState.isImageDisplayed
        }

        ZoomableViewport(
          modifier = Modifier.fillMaxSize(),
          state = viewportState,
          contentScale = ContentScale.Inside,
        ) {
          SubSamplingImage(
            modifier = Modifier.fillMaxSize(),
            state = imageState,
            contentDescription = null,
          )
        }
      }
    }

    composeTestRule.waitUntil { isImageDisplayed }
    composeTestRule.runOnIdle {
      dropshots.assertSnapshot(composeTestRule.activity)
    }
  }

  @Test fun image_smaller_than_viewport(@TestParameter size: SizeParam) {
    var isImageDisplayed = false

    composeTestRule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableViewportState()
        val imageState = rememberSubSamplingImageState(
          viewportState = viewportState,
          imageSource = ImageSource.asset("smol.jpg"),
        )
        LaunchedEffect(imageState.isImageDisplayed) {
          isImageDisplayed = imageState.isImageDisplayed
        }

        ZoomableViewport(
          modifier = Modifier.fillMaxSize(),
          state = viewportState,
          contentScale = ContentScale.Inside,
        ) {
          SubSamplingImage(
            modifier = size.modifier,
            state = imageState,
            contentDescription = null,
          )
        }
      }
    }

    composeTestRule.waitUntil { isImageDisplayed }
    composeTestRule.runOnIdle {
      dropshots.assertSnapshot(composeTestRule.activity)
    }
  }

  @Test fun various_content_alignment(
    @TestParameter alignment: AlignmentParam,
    @TestParameter size: SizeParam,
  ) {
    var isImageDisplayed = false

    composeTestRule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableViewportState()
        val imageState = rememberSubSamplingImageState(
          viewportState = viewportState,
          imageSource = ImageSource.asset("pahade.jpg"),
        )
        LaunchedEffect(imageState.isImageDisplayed) {
          isImageDisplayed = imageState.isImageDisplayed
        }

        ZoomableViewport(
          modifier = Modifier.fillMaxSize(),
          state = viewportState,
          contentAlignment = alignment.value,
          contentScale = ContentScale.Inside,
        ) {
          SubSamplingImage(
            modifier = Modifier.then(size.modifier),
            state = imageState,
            contentDescription = null,
          )
        }
      }
    }

    composeTestRule.waitUntil { isImageDisplayed }
    composeTestRule.runOnIdle {
      dropshots.assertSnapshot(composeTestRule.activity)
    }
  }

  @Test fun various_content_scale() {
    // todo.
  }

  @Test fun image_that_fills_both_width_and_height() {
    // todo.
  }

  @Test fun updating_of_image_works() {
    // todo:
    //  - content description should get updated.
  }

  @Test fun updating_of_image_works_when_zoomable_transformations_were_non_empty() {
    // todo.
  }

  @Test fun draw_base_tile_to_fill_gaps_in_foreground_tiles() {
    // todo.
  }

  @Test fun state_restoration() {
    // todo.
  }

  @Test fun zoomed_in_image() {
    // todo
  }

  @Test fun up_scaled_tiles_should_not_have_gaps_due_to_precision_loss() {
    var isImageDisplayed = false
    var imageTiles: List<CanvasRegionTile>? = null

    composeTestRule.setContent {
      ScreenScaffold {
        BoxWithConstraints {
          val imageState = rememberSubSamplingImageState(
            imageSource = ImageSource.asset("path.jpg"),
            transformation = ZoomableContentTransformation(
              viewportSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
              scale = ScaleFactor(scaleX = 1.1845919f, scaleY = 1.1845919f),
              offset = Offset(x = -2749.3718f, y = -1045.4058f),
              rotationZ = 0f,
              transformOrigin = TransformOrigin(0f, 0f)
            ),
          )
          LaunchedEffect(imageState.isImageDisplayed) {
            isImageDisplayed = imageState.isImageDisplayed
          }
          LaunchedEffect(imageState.tiles) {
            imageTiles = imageState.tiles
          }

          SubSamplingImage(
            modifier = Modifier.fillMaxSize(),
            state = imageState,
            contentDescription = null,
          )
        }
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 2_000) { isImageDisplayed }
    composeTestRule.runOnIdle {
      dropshots.assertSnapshot(composeTestRule.activity)

      assertThat(imageTiles!!.map { it.bounds }).containsExactly(
        IntRect(-1122, -1045, 503, 340),
        IntRect(-1122, 340, 503, 1726),
        IntRect(-1122, 1726, 503, 3293),
        IntRect(503, -1045, 2129, 340),
        IntRect(503, 340, 2129, 1726),
        IntRect(503, 1726, 2129, 3293),
      )
    }
  }

  @Test fun center_aligned_and_wrap_content() {
    var isImageDisplayed = false

    composeTestRule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableViewportState()
        val imageState = rememberSubSamplingImageState(
          viewportState = viewportState,
          imageSource = ImageSource.asset("smol.jpg"),
        )
        LaunchedEffect(imageState.isImageDisplayed) {
          isImageDisplayed = imageState.isImageDisplayed
        }

        ZoomableViewport(
          state = viewportState,
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
          contentScale = ContentScale.Inside,
        ) {
          SubSamplingImage(
            modifier = Modifier.wrapContentSize(),
            state = imageState,
            contentDescription = null,
          )
        }
      }
    }

    composeTestRule.waitUntil { isImageDisplayed }
    composeTestRule.runOnIdle {
      dropshots.assertSnapshot(composeTestRule.activity)
    }
  }

  @Test fun bitmaps_for_invisible_tiles_should_not_be_kept_in_memory() {
    // todo.
  }

  @Composable
  private fun ScreenScaffold(content: @Composable () -> Unit) {
    Box(
      Modifier
        .fillMaxSize()
        .background(Color(0xFF1C1A25))
    ) {
      content()
    }
  }

  @Suppress("unused")
  enum class SizeParam(val modifier: Modifier) {
    FillMaxSize(Modifier.fillMaxSize()),
    WrapContent(Modifier.wrapContentSize()),
  }

  @Suppress("unused")
  enum class AlignmentParam(val value: Alignment) {
    TopCenter(Alignment.TopCenter),
    Center(Alignment.Center),
    BottomCenter(Alignment.BottomCenter),
  }
}

private fun ThresholdValidator(thresholdPercent: Float): ResultValidator =
  ThresholdValidator(threshold = thresholdPercent / 100)
