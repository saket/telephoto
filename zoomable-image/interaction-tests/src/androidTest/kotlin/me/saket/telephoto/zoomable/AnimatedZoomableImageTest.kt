package me.saket.telephoto.zoomable

import android.app.Activity
import android.view.ViewConfiguration
import android.widget.Scroller
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import assertk.assertThat
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import kotlinx.coroutines.flow.Flow
import leakcanary.LeakAssertions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.util.ScreenshotTestActivity
import me.saket.telephoto.util.assetPainter
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class AnimatedZoomableImageTest {
  // Uses [ActivityScenarioRule] because UiAutomator gesture tests need real-time animations. [createAndroidComposeRule]
  // replaces the frame clock, preventing UiAutomator-triggered animations from running.
  @get:Rule val scenarioRule = ActivityScenarioRule(ScreenshotTestActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  @Before fun enableAnimations() {
    val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    uiAutomation.executeShellCommand("settings put global animator_duration_scale 1")
    uiAutomation.executeShellCommand("settings put global window_animation_scale 1")
    uiAutomation.executeShellCommand("settings put global transition_animation_scale 1")
  }

  @After
  fun tearDown() {
    LeakAssertions.assertNoLeaks()
  }

  @Test fun fling_animation_can_be_interrupted_by_pressing() {
    lateinit var state: ZoomableState

    scenario.onActivity { activity ->
      activity.setContentView(ComposeView(activity).apply {
        setContent {
          state = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 50f))
          ZoomableImage(
            modifier = Modifier.fillMaxSize(),
            image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
            contentDescription = "Zoomable image",
            state = rememberZoomableImageState(state),
          )
        }
      })
    }

    val robot = DeviceRobot(imageContentDescription = "Zoomable image")
    robot.doubleClick()
    waitUntil(5.seconds) { state.zoomFraction == 1f && !state.isAnimationRunning }

    val flingDuration = robot.fling(Direction.DOWN)
    check(state.isAnimationRunning) { "Fling decay animation did not start" }

    // Let the fling decay run visibly before interrupting.
    Thread.sleep(flingDuration.inWholeMilliseconds / 4)
    check(state.isAnimationRunning) { "Fling decay ended before it could be interrupted" }

    // Verify content is still moving.
    val offsetWhileFlinging = state.contentTransformation.offset
    Thread.sleep(100)
    assertThat(state.contentTransformation.offset).isNotEqualTo(offsetWhileFlinging)

    // Press anywhere to interrupt the fling.
    val offsetAtInterrupt = state.contentTransformation.offset
    robot.click()

    // The animation should stop almost immediately after the press.
    // A long timeout here would mask a broken interrupt mechanism
    // by letting the fling complete naturally.
    waitUntil(500.milliseconds) { !state.isAnimationRunning }

    // Verify content stopped near where it was when the click landed.
    val offsetAfterInterrupt = state.contentTransformation.offset
    val drift = (offsetAfterInterrupt - offsetAtInterrupt).getDistance()
    assertThat(drift).isLessThan(200f)
  }

  @Test fun double_tap_zoom_animation_can_be_interrupted_only_by_swipe() {
    lateinit var state: ZoomableState

    scenario.onActivity { activity ->
      activity.setContentView(ComposeView(activity).apply {
        setContent {
          state = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 10f))
          ZoomableImage(
            modifier = Modifier.fillMaxSize(),
            image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
            contentDescription = "Zoomable image",
            state = rememberZoomableImageState(state),
          )
        }
      })
    }

    val robot = DeviceRobot(imageContentDescription = "Zoomable image")

    // Double-tap to start zoom animation.
    robot.doubleClick()
    waitUntil { state.isAnimationRunning }
    Thread.sleep(100)

    // A press should NOT interrupt the zoom animation.
    robot.click()
    waitUntil(5.seconds) { !state.isAnimationRunning && state.zoomFraction == 1f }

    // Reset zoom.
    robot.doubleClick()
    waitUntil(5.seconds) { !state.isAnimationRunning && state.zoomFraction == 0f }

    // Double-tap again to start another zoom animation.
    robot.doubleClick()
    waitUntil { state.isAnimationRunning }

    // A swipe SHOULD interrupt the zoom animation.
    robot.swipe(Direction.LEFT)
    waitUntil(5.seconds) { !state.isAnimationRunning }

    // Zoom should NOT have reached max — it was interrupted.
    assertThat(state.zoomFraction!!).isLessThan(1f)
  }
}


private class DeviceRobot(imageContentDescription: String) {
  val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  val image: UiObject2 = device.wait(Until.findObject(By.desc(imageContentDescription)), 5_000)
    ?: error("Could not find UiObject2 with content description '$imageContentDescription'")

  fun doubleClick() {
    val x = image.visibleBounds.centerX()
    val y = image.visibleBounds.centerY()
    device.click(x, y)
    Thread.sleep(ViewConfiguration.getDoubleTapTimeout() / 2L)
    device.click(x, y)
  }

  fun click() {
    device.click(image.visibleBounds.centerX(), image.visibleBounds.centerY())
  }

  fun fling(direction: Direction): Duration {
    val cx = image.visibleBounds.centerX()
    val cy = image.visibleBounds.centerY()
    val distance = image.visibleBounds.height() / 4
    val steps = 5

    // This uses UiDevice#swipe() with few steps so the gesture completes quickly,
    // leaving the decay animation still running when this method returns.
    //
    // [UiObject2.fling] can't be used because its gesture takes several
    // seconds, during which the decay animation starts and completes.
    when (direction) {
      Direction.DOWN -> device.swipe(cx, cy - distance / 2, cx, cy + distance / 2, steps)
      else -> error("unused $direction")
    }
    return estimatedFlingDuration(distance = distance, steps = steps)
  }

  /**
   * Estimate the duration of a fling decay using [Scroller], which uses the
   * same spline-based decay as Compose's `splineBasedDecay()`.
   */
  private fun estimatedFlingDuration(distance: Int, steps: Int): Duration {
    // UiAutomator takes ~5ms per step.
    val gestureDurationMs = steps * 5
    val rawVelocity = distance.toFloat() / gestureDurationMs * 1_000f

    // Compose's VelocityTracker applies smoothing that reduces the
    // detected velocity to roughly 25% of the raw calculation.
    val estimatedComposeVelocity = (rawVelocity * 0.25f).toInt()

    val scroller = Scroller(InstrumentationRegistry.getInstrumentation().targetContext)
    scroller.fling(
      /* startX = */ 0,
      /* startY = */ 0,
      /* velocityX = */ 0,
      /* velocityY = */ estimatedComposeVelocity,
      /* minX = */ 0,
      /* maxX = */ 0,
      /* minY = */ Int.MIN_VALUE,
      /* maxY = */ Int.MAX_VALUE
    )
    return scroller.duration.milliseconds
  }

  fun swipe(direction: Direction) {
    image.setGestureMargin(device.displayWidth / 5)
    image.swipe(direction, /* percent = */ 0.5f)
  }
}

// Gross, but works.
private fun <A : Activity> ActivityScenario<A>.activity(): A {
  var activity: A? = null
  onActivity { activity = it }
  return activity!!
}

private fun waitUntil(timeout: Duration = 1.seconds, condition: () -> Boolean) {
  val mark = TimeSource.Monotonic.markNow()
  while (!condition()) {
    check(mark.elapsedNow() < timeout) { "Timed out waiting for condition" }
    Thread.sleep(50)
  }
}

@Composable
private fun ZoomableImageSource.Companion.asset(assetName: String, subSample: Boolean): ZoomableImageSource {
  return remember(assetName) {
    object : ZoomableImageSource {
      @Composable
      override fun resolve(canvasSize: Flow<Size>): ZoomableImageSource.ResolveResult {
        return ZoomableImageSource.ResolveResult(
          delegate = if (subSample) {
            ZoomableImageSource.SubSamplingDelegate(SubSamplingImageSource.asset(assetName))
          } else {
            ZoomableImageSource.PainterDelegate(assetPainter(assetName))
          }
        )
      }
    }
  }
}

