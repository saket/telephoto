package me.saket.telephoto.zoomable

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeWithVelocity
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Condition
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import com.dropbox.dropshots.Dropshots
import leakcanary.LeakAssertions
import me.saket.telephoto.util.CiScreenshotValidator
import me.saket.telephoto.util.prepareForScreenshotTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class AnimatedZoomableImageTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>(
    effectContext = object : MotionDurationScale {
      override val scaleFactor: Float get() = 1f
    }
  )

  @get:Rule val dropshots = Dropshots(
    filenameFunc = { it },
    resultValidator = CiScreenshotValidator(
      context = { rule.activity },
      tolerancePercentOnLocal = 0f,
      tolerancePercentOnCi = 0.01f,
    )
  )

  @Before
  fun setup() {
    rule.activityRule.scenario.onActivity {
      it.prepareForScreenshotTest()
    }
  }

  @After
  fun tearDown() {
    LeakAssertions.assertNoLeaks()
  }

  @Ignore("Gotta figure out how to interact with the UI using UiAutomator")
  @Test fun fling_animations_can_be_interrupted_by_pressing_anywhere() {
    lateinit var state: ZoomableState

    rule.setContent {
      state = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 10f)
      )
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .semantics {
            testTag = "zoomable"
            testTagsAsResourceId = true
          },
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        contentDescription = null,
        state = rememberZoomableImageState(state),
        onClick = { error("click listener should not get called") },
        onLongClick = { error("long click listener should not get called") },
      )
    }

    rule.onNodeWithTag("zoomable").performTouchInput {
      doubleClick(position = center)
    }
    rule.waitUntil(5_000) { state.zoomFraction == 1f }

    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val zoomableObject = device.findObject(By.res("zoomable"))

    // todo: these interactions aren't working.
    zoomableObject.click()
    Thread.sleep(android.view.ViewConfiguration.getDoubleTapTimeout().toLong())
    zoomableObject.click()

    zoomableObject.swipe(Direction.LEFT, 0.1f)
    zoomableObject.fling(Direction.LEFT)
  }
}
