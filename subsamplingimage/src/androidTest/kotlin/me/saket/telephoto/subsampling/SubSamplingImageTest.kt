package me.saket.telephoto.subsampling

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dropbox.dropshots.Dropshots
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.SubSamplingImageEventListener
import me.saket.telephoto.subsamplingimage.SubSamplingImageState
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomableViewport
import me.saket.telephoto.zoomable.rememberZoomableViewportState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(TestParameterInjector::class)
class SubSamplingImageTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val dropshots = Dropshots()
  @get:Rule val testName = TestName()

  @Before
  fun setup() {
    composeTestRule.activityRule.scenario.onActivity {
      it.actionBar?.hide()

      // Hide system bars in an attempt to reduce differences
      // in from screenshots generated on different devices.
      it.window.setDecorFitsSystemWindows(false)
      it.window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }
    SubSamplingImageState.showTileBounds = true
  }

  @Test fun canary() = runBlocking {
    val onImageDisplayed = Mutex(locked = true)

    composeTestRule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableViewportState()
        val imageState = rememberSubSamplingImageState(
          viewportState = viewportState,
          imageSource = ImageSource.asset("pahade.jpg"),
          eventListener = onImageDisplayed { onImageDisplayed.unlock() }
        )
        ZoomableViewport(
          modifier = Modifier.fillMaxSize(),
          state = viewportState
        ) {
          SubSamplingImage(
            modifier = Modifier.fillMaxSize(),
            state = imageState,
            contentDescription = null,
          )
        }
      }
    }

    onImageDisplayed.withLock(timeout = 1.seconds) {
      dropshots.assertSnapshot(composeTestRule.activity)
    }
  }

  @Test fun image_smaller_than_viewport(@TestParameter size: SizeParam) = runBlocking {
    val onImageDisplayed = Mutex(locked = true)

    composeTestRule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableViewportState()
        val imageState = rememberSubSamplingImageState(
          viewportState = viewportState,
          imageSource = ImageSource.asset("smol.jpg"),
          eventListener = onImageDisplayed { onImageDisplayed.unlock() }
        )
        ZoomableViewport(
          modifier = Modifier.fillMaxSize(),
          state = viewportState
        ) {
          SubSamplingImage(
            modifier = Modifier.then(size.modifier),
            state = imageState,
            contentDescription = null,
          )
        }
      }
    }

    onImageDisplayed.withLock(timeout = 1.seconds) {
      dropshots.assertSnapshot(composeTestRule.activity)
    }
  }

  @Test fun various_content_alignment(
    @TestParameter alignment: AlignmentParam,
    @TestParameter size: SizeParam,
  ) = runBlocking {
    val onImageDisplayed = Mutex(locked = true)

    composeTestRule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableViewportState()
        val imageState = rememberSubSamplingImageState(
          viewportState = viewportState,
          imageSource = ImageSource.asset("pahade.jpg"),
          eventListener = onImageDisplayed { onImageDisplayed.unlock() }
        )
        ZoomableViewport(
          modifier = Modifier.fillMaxSize(),
          state = viewportState,
          contentAlignment = alignment.value,
        ) {
          SubSamplingImage(
            modifier = Modifier.then(size.modifier),
            state = imageState,
            contentDescription = null,
          )
        }
      }
    }

    onImageDisplayed.withLock(timeout = 1.seconds) {
      dropshots.assertSnapshot(composeTestRule.activity)
    }
  }

  @Test fun image_that_fills_height() {
    // todo.
  }

  @Test fun image_that_fills_width() {
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
  private fun onImageDisplayed(action: () -> Unit): SubSamplingImageEventListener {
    return remember {
      object : SubSamplingImageEventListener {
        override fun onImageDisplayed() = action()
      }
    }
  }

  private suspend inline fun <T> Mutex.withLock(timeout: Duration, crossinline action: () -> T) {
    withTimeoutOrNull(timeout) {
      lock()
    }
    try {
      action()
    } finally {
      unlock()
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
