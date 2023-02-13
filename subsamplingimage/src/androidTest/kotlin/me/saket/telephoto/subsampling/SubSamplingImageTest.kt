package me.saket.telephoto.subsampling

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dropbox.dropshots.Dropshots
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.SubSamplingImageEventListener
import me.saket.telephoto.subsamplingimage.SubSamplingImageState
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomableViewport
import me.saket.telephoto.zoomable.rememberZoomableState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class SubSamplingImageTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val dropshots = Dropshots()
  @get:Rule val testName = TestName()

  @Before
  fun setup() {
    composeTestRule.activityRule.scenario.onActivity {
      it.title = "Telephoto"
    }
    SubSamplingImageState.showTileBounds = true
  }

  @Test fun canary() = runBlocking {
    val onImageDisplayed = Mutex(locked = true)

    composeTestRule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableState()
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

    onImageDisplayed.withLock {
      dropshots.assertSnapshot(composeTestRule.activity)
    }
  }

  @Test fun image_smaller_than_viewport(@TestParameter wrapContent: Boolean) = runBlocking {
    val onImageDisplayed = Mutex(locked = true)

    composeTestRule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableState()
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
            modifier = if (wrapContent) Modifier.wrapContentSize() else Modifier.fillMaxSize(),
            state = imageState,
            contentDescription = null,
          )
        }
      }
    }

    onImageDisplayed.withLock {
      dropshots.assertSnapshot(composeTestRule.activity, name = testName.methodName + "_(wrap_content=$wrapContent)")
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

  @Composable
  private fun ScreenScaffold(content: @Composable () -> Unit) {
    Box(
      Modifier
        .fillMaxSize()
        .background(Color.DarkGray)
    ) {
      content()
    }
  }

  @Composable
  private fun onImageDisplayed(action: () -> Unit): SubSamplingImageEventListener {
    return remember {
      object : SubSamplingImageEventListener {
        override fun onImageDisplayed() {
          action()
        }
      }
    }
  }
}
