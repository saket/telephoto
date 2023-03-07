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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import com.dropbox.dropshots.Dropshots
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
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

  @Test fun canary() = runTest(1.seconds) {
    composeTestRule.setContent {
      ScreenScaffold {
        val painter = assetPainter("fox_1500.jpg")
        val viewportState = rememberZoomableViewportState()
        LaunchedEffect(painter) {
          viewportState.setContentLocation(
            ZoomableContentLocation.fitToBoundsAndAlignedToCenter(painter.intrinsicSize)
          )
        }

        ZoomableViewport(
          state = viewportState,
          contentScale = ContentScale.Fit,
        ) {
          Image(
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer(viewportState.contentTransformation),
            painter = painter,
            contentDescription = null,
          )
        }
      }
    }
    dropshots.assertSnapshot(composeTestRule.activity)
  }

  @Test fun zoom_in() = runTest {
    var finalScale = ScaleFactor.Unspecified

    composeTestRule.setContent {
      ScreenScaffold {
        val painter = assetPainter("fox_1500.jpg")
        val viewportState = rememberZoomableViewportState(maxZoomFactor = 2f)
        LaunchedEffect(painter) {
          viewportState.setContentLocation(
            ZoomableContentLocation.fitToBoundsAndAlignedToCenter(painter.intrinsicSize)
          )
        }

        ZoomableViewport(
          modifier = Modifier.testTag("viewport"),
          state = viewportState,
          contentScale = ContentScale.Fit,
        ) {
          Image(
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer(viewportState.contentTransformation),
            painter = painter,
            contentDescription = null,
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

@OptIn(ExperimentalCoroutinesApi::class)
private fun runTest(timeout: Duration, testBody: suspend TestScope.() -> Unit) {
  runTest(dispatchTimeoutMs = timeout.inWholeMilliseconds, testBody = testBody)
}
