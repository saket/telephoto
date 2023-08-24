package me.saket.telephoto.benchmark

import android.content.Intent
import android.net.Uri
import android.view.ViewConfiguration
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class PerformanceBenchmark {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun tiling() = benchmarkRule.measureRepeated(
    packageName = "me.saket.telephoto.sample",
    metrics = listOf(
      StartupTimingMetric(),
      FrameTimingMetric(),
      MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
      TraceSectionMetric("decodeRegion")
    ),
    iterations = 5,
    startupMode = StartupMode.COLD,
  ) {
    device.pressHome()
    startActivityAndWait(Intent(Intent.ACTION_VIEW, Uri.parse("telephoto://performance")))

    val robot = DeviceRobot(device)
    robot.waitUntilBaseTileIsLoaded()

    val image = robot.findZoomableImage()
    robot.doubleTapToZoom(image)
    robot.waitUntilTilesAreLoaded()
    Thread.sleep(500)

    image.setGestureMargin(device.displayWidth / 5) // Avoid interfering with gesture navigation.
    image.fling(Direction.DOWN)
    robot.waitUntilTilesAreLoaded()
  }
}

private class DeviceRobot(val device: UiDevice) {
  fun findZoomableImage(): UiObject2 {
    return device.findObject(By.desc("Zoomable image"))
  }

  fun waitUntilTilesAreLoaded(timeout: Duration = 25.seconds) {
    if (!device.wait(Until.hasObject(By.text("Tiles loaded")), timeout.inWholeMilliseconds)) {
      error("Could not load image in $timeout")
    }
  }

  fun waitUntilBaseTileIsLoaded() {
    waitUntilTilesAreLoaded(timeout = 25.seconds)
  }

  fun doubleTapToZoom(image: UiObject2) {
    val x = image.visibleBounds.centerX()
    val y = image.visibleBounds.centerY()

    device.click(x, y)
    Thread.sleep(ViewConfiguration.getDoubleTapTimeout() / 2L)
    device.click(x, y)
  }
}
