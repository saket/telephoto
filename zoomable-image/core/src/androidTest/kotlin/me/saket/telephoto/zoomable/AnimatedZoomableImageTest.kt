package me.saket.telephoto.zoomable

import android.app.Activity
import android.view.ViewConfiguration
import android.widget.Scroller
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
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
import assertk.assertions.isFalse
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import com.dropbox.dropshots.Dropshots
import kotlinx.coroutines.flow.Flow
import leakcanary.LeakAssertions
import me.saket.telephoto.util.CiScreenshotValidator
import me.saket.telephoto.util.ScreenshotTestActivity
import me.saket.telephoto.util.assetPainter
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class AnimatedZoomableImageTest {
  @get:Rule val scenarioRule = ActivityScenarioRule(ScreenshotTestActivity::class.java)
  private val scenario get() = scenarioRule.scenario
  @get:Rule val testName = TestName()
  @get:Rule val dropshots = Dropshots(
    filenameFunc = { _, testName -> testName },
    resultValidator = CiScreenshotValidator(
      context = { scenario.activity() },
      tolerancePercentOnLocal = 0f,
      tolerancePercentOnCi = 0.01f,
    ),
  )

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

  @Test fun placeholder_crossfade_animation_plays_when_image_loads() {
    val crossfadeDuration = 2.seconds
    lateinit var imageState: ZoomableImageState
    var imageLoaded by mutableStateOf(false)

    scenario.onActivity { activity ->
      activity.setContentView(ComposeView(activity).apply {
        setContent {
          imageState = rememberZoomableImageState(rememberZoomableState())
          ZoomableImage(
            modifier = Modifier.fillMaxSize(),
            image = crossfadeImageSource(
              placeholder = assetPainter("fox_25.png"),
              fullImage = assetPainter("fox_1500.jpg"),
              crossfadeDuration = crossfadeDuration,
              imageLoaded = imageLoaded,
            ),
            contentDescription = null,
            state = imageState,
          )
        }
      })
    }

    // Wait for placeholder to be displayed.
    waitUntil(5.seconds) { imageState.isPlaceholderDisplayed }
    assertThat(imageState.isImageDisplayed).isFalse()
    scenario.onActivity { activity ->
      dropshots.assertSnapshot(activity, testName.methodName + "_placeholder")
    }

    // Trigger image load — this starts the crossfade animation.
    imageLoaded = true
    waitUntil(5.seconds) { imageState.isImageDisplayed }

    // Let the crossfade progress to a visible midpoint.
    Thread.sleep(crossfadeDuration.inWholeMilliseconds / 4)

    // The placeholder should still be visible mid-crossfade.
    assertThat(imageState.isPlaceholderDisplayed).isTrue()
    scenario.onActivity { activity ->
      dropshots.assertSnapshot(activity, testName.methodName + "_mid_crossfade")
    }

    // Wait for crossfade to complete.
    waitUntil(5.seconds) { !imageState.isPlaceholderDisplayed }
    assertThat(imageState.isImageDisplayed).isTrue()
    scenario.onActivity { activity ->
      dropshots.assertSnapshot(activity, testName.methodName + "_full_image")
    }
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
private fun crossfadeImageSource(
  placeholder: Painter,
  fullImage: Painter,
  crossfadeDuration: Duration,
  imageLoaded: Boolean,
): ZoomableImageSource {
  return object : ZoomableImageSource {
    @Composable
    override fun resolve(canvasSize: Flow<Size>): ZoomableImageSource.ResolveResult {
      return ZoomableImageSource.ResolveResult(
        delegate = if (imageLoaded) ZoomableImageSource.PainterDelegate(fullImage) else null,
        crossfadeDuration = crossfadeDuration,
        placeholder = placeholder,
      )
    }
  }
}
