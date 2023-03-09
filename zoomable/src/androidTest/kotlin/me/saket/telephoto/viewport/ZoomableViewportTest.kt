package me.saket.telephoto.viewport

import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import com.dropbox.dropshots.Dropshots
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class ZoomableViewportTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val dropshots = Dropshots()
  @get:Rule val testName = TestName()

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
    composeTestRule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableViewportState()
        ZoomableViewport(
          state = viewportState,
          contentScale = ContentScale.Fit,
        ) {
          ImageAsset(
            viewportState = viewportState,
            assetName = "fox_1500.jpg"
          )
        }
      }
    }
    dropshots.assertSnapshot(composeTestRule.activity)
  }

  @Test fun zoom_in() {
    var finalScale = ScaleFactor.Unspecified

    composeTestRule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableViewportState(maxZoomFactor = 2f)
        ZoomableViewport(
          modifier = Modifier.testTag("viewport"),
          state = viewportState,
          contentScale = ContentScale.Fit,
        ) {
          ImageAsset(
            viewportState = viewportState,
            assetName = "fox_1500.jpg"
          )
        }

        LaunchedEffect(viewportState.contentTransformation) {
          finalScale = viewportState.contentTransformation.scale
        }
      }
    }

    composeTestRule.onNodeWithTag("viewport").performTouchInput {
      pinchToZoomBy(visibleSize.center / 2f)
    }
    assertThat(finalScale.scaleX).isWithin(0.01f).of(2.2f)
    assertThat(finalScale.scaleY).isWithin(0.01f).of(2.2f)
    dropshots.assertSnapshot(composeTestRule.activity)
  }

  @Test fun retain_transformations_across_state_restorations() {
    val stateRestorationTester = StateRestorationTester(composeTestRule)

    stateRestorationTester.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableViewportState(maxZoomFactor = 2f)
        ZoomableViewport(
          modifier = Modifier.testTag("viewport"),
          state = viewportState,
          contentScale = ContentScale.Fit,
        ) {
          ImageAsset(
            viewportState = viewportState,
            assetName = "fox_1500.jpg"
          )
        }
      }
    }

    with(composeTestRule.onNodeWithTag("viewport")) {
      performTouchInput {
        pinchToZoomBy(visibleSize.center / 2f)
      }
      performTouchInput {
        swipeLeft(startX = center.x, endX = centerLeft.x)
      }
    }
    composeTestRule.runOnIdle {
      dropshots.assertSnapshot(composeTestRule.activity)
    }

    stateRestorationTester.emulateSavedInstanceStateRestore()

    composeTestRule.runOnIdle {
      dropshots.assertSnapshot(composeTestRule.activity)
    }
  }

  @Test fun alignments_and_scales(
    @TestParameter alignment: AlignmentParam,
    @TestParameter contentScale: ContentScaleParam,
  ) {
    composeTestRule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableViewportState(maxZoomFactor = 1.5f)
        ZoomableViewport(
          modifier = Modifier.testTag("viewport"),
          state = viewportState,
          contentScale = contentScale.value,
          contentAlignment = alignment.value,
        ) {
          ImageAsset(
            viewportState = viewportState,
            assetName = "fox_250.jpg"
          )
        }
      }
    }
    composeTestRule.runOnIdle {
      dropshots.assertSnapshot(composeTestRule.activity, testName.methodName)
    }

    with(composeTestRule.onNodeWithTag("viewport")) {
      performTouchInput {
        val by = visibleSize.center / 2f
        pinch(
          start0 = centerLeft,
          start1 = centerLeft,
          end0 = centerLeft - by.toOffset(),
          end1 = centerLeft + by.toOffset(),
        )
      }
    }
    composeTestRule.runOnIdle {
      dropshots.assertSnapshot(composeTestRule.activity, testName.methodName + "_zoomed")
    }

    with(composeTestRule.onNodeWithTag("viewport")) {
      performTouchInput {
        swipeLeft(startX = center.x, endX = centerLeft.x)
      }
    }
    composeTestRule.runOnIdle {
      dropshots.assertSnapshot(composeTestRule.activity, testName.methodName + "_zoomed_panned")
    }
  }

  @Test fun updating_of_content_alignment() {
    var contentAlignment by mutableStateOf(Alignment.BottomCenter)

    composeTestRule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableViewportState()
        ZoomableViewport(
          state = viewportState,
          contentScale = ContentScale.Fit,
          contentAlignment = contentAlignment,
        ) {
          ImageAsset(
            viewportState = viewportState,
            assetName = "fox_1500.jpg"
          )
        }
      }
    }
    dropshots.assertSnapshot(composeTestRule.activity, testName.methodName + "_before_updating_alignment")

    contentAlignment = Alignment.TopCenter
    composeTestRule.runOnIdle {
      dropshots.assertSnapshot(composeTestRule.activity, testName.methodName + "_after_updating_alignment")
    }
  }

  @Test fun updating_of_content_scale() {
    var contentScale by mutableStateOf(ContentScale.Crop)

    composeTestRule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableViewportState()
        ZoomableViewport(
          state = viewportState,
          contentScale = contentScale,
        ) {
          ImageAsset(
            viewportState = viewportState,
            assetName = "fox_1500.jpg"
          )
        }
      }
    }
    dropshots.assertSnapshot(composeTestRule.activity, testName.methodName + "_before_updating_scale")

    contentScale = ContentScale.Inside
    composeTestRule.runOnIdle {
      dropshots.assertSnapshot(composeTestRule.activity, testName.methodName + "_after_updating_scale")
    }
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

  @Composable
  private fun ImageAsset(
    viewportState: ZoomableViewportState,
    assetName: String
  ) {
    val painter = assetPainter(assetName)
    LaunchedEffect(painter) {
      viewportState.setContentLocation(
        ZoomableContentLocation.fitToBoundsAndAlignedToCenter(painter.intrinsicSize)
      )
    }

    Image(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer(viewportState.contentTransformation),
      painter = painter,
      contentDescription = null,
    )
  }

  @Suppress("unused")
  enum class AlignmentParam(val value: Alignment) {
    TopCenter(Alignment.TopCenter),
    Center(Alignment.Center),
    BottomCenter(Alignment.BottomCenter),
  }

  @Suppress("unused")
  enum class ContentScaleParam(val value: ContentScale) {
    Crop(ContentScale.Crop),
    Fit(ContentScale.Fit),
    Inside(ContentScale.Inside),
  }
}

private fun TouchInjectionScope.pinchToZoomBy(by: IntOffset) {
  pinch(
    start0 = center,
    start1 = center,
    end0 = center - by.toOffset(),
    end1 = center + by.toOffset(),
  )
}

@Composable
private fun assetPainter(fileName: String): Painter {
  val context = LocalContext.current
  return remember {
    val stream = context.assets.open(fileName)
    BitmapPainter(BitmapFactory.decodeStream(stream).asImageBitmap())
  }
}
