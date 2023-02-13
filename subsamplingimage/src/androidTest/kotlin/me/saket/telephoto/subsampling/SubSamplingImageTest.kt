package me.saket.telephoto.subsampling

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dropbox.dropshots.Dropshots
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
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubSamplingImageTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val dropshots = Dropshots()

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
          imageSource = ImageSource.asset("pahade.jpeg"),
          eventListener = remember {
            object : SubSamplingImageEventListener {
              override fun onImageDisplayed() {
                onImageDisplayed.unlock()
              }
            }
          }
        )
        ZoomableViewport(
          modifier = Modifier.fillMaxSize(),
          state = viewportState
        ) {
          SubSamplingImage(
            modifier = Modifier.fillMaxSize(),
            state = imageState,
          )
        }
      }
    }

    onImageDisplayed.withLock {
      dropshots.assertSnapshot(composeTestRule.activity)
    }
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
}
