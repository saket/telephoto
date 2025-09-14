package me.saket.telephoto.zoomable

import android.app.Activity
import android.graphics.Bitmap
import android.view.PixelCopy
import android.view.ViewConfiguration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.viewinterop.AndroidView
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import com.dropbox.dropshots.Dropshots
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.runTest
import leakcanary.LeakAssertions
import me.saket.telephoto.util.ActivityRecreationTester
import me.saket.telephoto.util.CiScreenshotValidator
import me.saket.telephoto.util.ScreenshotTestActivity
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.rules.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor

class ZoomablePeekOverlayTest {
  @get:Rule val rule = createAndroidComposeRule<ScreenshotTestActivity>()
  @get:Rule val timeout = Timeout.seconds(30)!!
  @get:Rule val testName = TestName()

  private val screenshotValidator = CiScreenshotValidator(
    context = { rule.activity },
    tolerancePercentOnLocal = 0f,
    tolerancePercentOnCi = 0.01f,
  )
  @get:Rule val dropshots = Dropshots(
    filenameFunc = { _, testName -> testName },
    resultValidator = screenshotValidator,
  )

  @After fun tearDown() {
    LeakAssertions.assertNoLeaks()
  }

  @Test fun zoom_in_and_release() = runTest {
    lateinit var state: ZoomablePeekOverlayState

    rule.setContent {
      Box(Modifier.fillMaxSize(), Alignment.Center) {
        state = rememberZoomablePeekOverlayState()
        Box(
          Modifier
            .size(200.dp)
            .zoomablePeekOverlay(state)
            .background(Color.Green)
            .testTag("content")
        )
      }
    }

    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.takePixelCopyScreenshot(), testName.methodName + "_[before_zoom]")
    }

    rule.onNodeWithTag("content").performTouchInput {
      val noUpScope = object : TouchInjectionScope by this {
        override fun up(pointerId: Int) = Unit
      }
      noUpScope.pinchToZoomInBy(IntOffset(5, 5))
    }
    rule.runOnIdle {
      assertThat(state.isZoomedIn).isTrue()
      dropshots.assertSnapshot(rule.activity.takePixelCopyScreenshot(), testName.methodName + "_[during_zoom]")
    }

    rule.onNodeWithTag("content").performTouchInput {
      this.cancel()
    }
    rule.waitUntil { !state.isZoomedIn }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.takePixelCopyScreenshot(), testName.methodName + "_[after_zoom]")
    }
  }

  @Test fun custom_overlay_decoration() = runTest {
    lateinit var state: ZoomablePeekOverlayState
    rule.setContent {
      Box(Modifier.fillMaxSize(), Alignment.Center) {
        val overlayDecoration = ZoomablePeekOverlayBackdrop {
          Box(
            Modifier
              .fillMaxSize()
              .background(Brush.linearGradient(listOf(Color(0xFF2be4dc), Color(0xFF243484))))
          )
        }

        state = rememberZoomablePeekOverlayState()
        Box(
          Modifier
            .size(200.dp)
            .zoomablePeekOverlay(state, overlayDecoration)
            .background(Color.Yellow)
            .testTag("content")
        )
      }
    }

    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.takePixelCopyScreenshot(), testName.methodName + "_[before_zoom]")
    }

    rule.onNodeWithTag("content").performTouchInput {
      val noUpScope = object : TouchInjectionScope by this {
        override fun up(pointerId: Int) = Unit
      }
      noUpScope.pinchToZoomInBy(IntOffset(5, 5))
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.takePixelCopyScreenshot(), testName.methodName + "_[during_zoom]")
    }

    rule.onNodeWithTag("content").performTouchInput {
      this.cancel()
    }
    rule.waitUntil { !state.isZoomedIn }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.takePixelCopyScreenshot(), testName.methodName + "_[after_zoom]")
    }
  }

  @Test fun brush_overlay_decoration() = runTest {
    lateinit var state: ZoomablePeekOverlayState
    rule.setContent {
      Box(Modifier.fillMaxSize(), Alignment.Center) {
        state = rememberZoomablePeekOverlayState()
        val overlayDecoration = ZoomablePeekOverlayBackdrop.scrim(
          Brush.horizontalGradient(listOf(Color(0xFF2be4dc), Color(0xFF243484)))
        )
        Box(
          Modifier
            .size(100.dp)
            .zoomablePeekOverlay(state, overlayDecoration)
            .background(Color.Yellow)
            .testTag("content")
        )
      }
    }

    rule.onNodeWithTag("content").performTouchInput {
      val noUpScope = object : TouchInjectionScope by this {
        override fun up(pointerId: Int) = Unit
      }
      noUpScope.pinchToZoomInBy(IntOffset(5, 5))
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.takePixelCopyScreenshot())
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Test fun clickable_modifier_works_after_overlay_modifier() {
    var onClickCount = 0
    var onLongClickCount = 0
    var onDoubleClickCount = 0

    rule.setContent {
      Box(Modifier.fillMaxSize(), Alignment.Center) {
        Box(
          Modifier
            .size(200.dp)
            .zoomablePeekOverlay(rememberZoomablePeekOverlayState())
            .background(Color.Yellow)
            .combinedClickable(
              onClick = { onClickCount++ },
              onLongClick = { onLongClickCount++ },
              onDoubleClick = { onDoubleClickCount++ },
            )
            .testTag("content")
        )
      }
    }

    rule.onNodeWithTag("content").run {
      performClick()
      rule.mainClock.advanceTimeBy(ViewConfiguration.getDoubleTapTimeout().toLong())

      performTouchInput { longClick() }
      performTouchInput { doubleClick() }
    }

    rule.runOnIdle {
      assertThat(onClickCount).isEqualTo(1)
      assertThat(onDoubleClickCount).isEqualTo(1)
      assertThat(onDoubleClickCount).isEqualTo(1)
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Test fun clickable_modifier_works_before_overlay_modifier() {
    var onClickCount = 0
    var onLongClickCount = 0
    var onDoubleClickCount = 0

    rule.setContent {
      Box(Modifier.fillMaxSize(), Alignment.Center) {
        Box(
          Modifier
            .size(200.dp)
            .combinedClickable(
              onClick = { onClickCount++ },
              onLongClick = { onLongClickCount++ },
              onDoubleClick = { onDoubleClickCount++ },
            )
            .zoomablePeekOverlay(rememberZoomablePeekOverlayState())
            .background(Color.Yellow)
            .testTag("content")
        )
      }
    }

    rule.onNodeWithTag("content").run {
      performClick()
      rule.mainClock.advanceTimeBy(ViewConfiguration.getDoubleTapTimeout().toLong())

      performTouchInput { longClick() }
      performTouchInput { doubleClick() }
    }

    rule.runOnIdle {
      assertThat(onClickCount).isEqualTo(1)
      assertThat(onDoubleClickCount).isEqualTo(1)
      assertThat(onDoubleClickCount).isEqualTo(1)
    }
  }

  @Test fun updates_to_content_are_reflected_in_the_zoomed_overlay() = runTest {
    lateinit var state: ZoomablePeekOverlayState
    var contentText by mutableStateOf("text set before zoom")
    var contentAlignment by mutableStateOf(Alignment.TopCenter)

    rule.setContent {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(50.dp),
        contentAlignment = contentAlignment,
      ) {
        state = rememberZoomablePeekOverlayState()
        Box(
          Modifier
            .size(100.dp)
            .zoomablePeekOverlay(state)
            .background(Color.White)
            .testTag("content")
        ) {
          BasicText(
            modifier = Modifier.padding(8.dp),
            text = contentText,
            style = TextStyle.Default.copy(color = Color.Black, fontSize = 14.sp),
          )
        }
      }
    }

    rule.onNodeWithTag("content").performTouchInput {
      val noUpScope = object : TouchInjectionScope by this {
        override fun up(pointerId: Int) = Unit
      }
      noUpScope.pinchToZoomInBy(IntOffset(5, 5))
    }
    rule.runOnIdle {
      assertThat(state.isZoomedIn).isTrue()
      dropshots.assertSnapshot(rule.activity.takePixelCopyScreenshot(), testName.methodName + "_[initial]")
    }

    // Content can be updated.
    contentText = "text updated after zoom"
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.takePixelCopyScreenshot(), testName.methodName + "_[content_updated]")
    }

    // Content's position can also be updated.
    contentText = "position updated"
    contentAlignment = Alignment.BottomCenter
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.takePixelCopyScreenshot(), testName.methodName + "_[position_updated]")
    }
  }

  // Regression test for https://github.com/saket/telephoto/commit/70044719301933df178d89348770dc4b31bd0660
  @Test fun list_items_canary() {
    lateinit var listState: LazyListState
    val scrollTriggers = Channel<Int>(capacity = 1)
    val itemCount = 10

    rule.setContent {
      listState = rememberLazyListState()
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        items(itemCount) {
          Box(
            Modifier
              .fillMaxWidth()
              .height(300.dp)
              .zoomablePeekOverlay(rememberZoomablePeekOverlayState())
              .background(Color.Yellow)
          )
        }
      }
      LaunchedEffect(Unit) {
        scrollTriggers.consumeAsFlow().collect { index ->
          listState.animateScrollToItem(index)
        }
      }
    }

    // Scroll to the last item.
    rule.runOnIdle {
      assertThat(listState.firstVisibleItemIndex == 0)
      scrollTriggers.trySend(itemCount - 1)
    }
    rule.waitUntil {
      listState.layoutInfo.visibleItemsInfo.last().index == 9
    }

    // Scroll back to the first item.
    rule.runOnIdle {
      scrollTriggers.trySend(0)
    }
    rule.waitUntil {
      listState.firstVisibleItemIndex == 0
    }
  }

  @Test fun disable_overlay_when_canvas_is_software_accelerated() {
    lateinit var state: ZoomablePeekOverlayState
    rule.setContent {
      Box(Modifier.fillMaxSize(), Alignment.Center) {
        state = rememberZoomablePeekOverlayState()
        Box(
          Modifier
            .size(200.dp)
            .zoomablePeekOverlay(state)
            .background(Color.Yellow)
            .testTag("content")
        )
      }
    }

    rule.runOnIdle {
      rule.runOnUiThread {
        dropshots.assertSnapshot(
          rule.activity.takeScreenshotInSoftwareAccelerationMode(),
          testName.methodName + "_[before_zoom]"
        )
      }
    }

    rule.onNodeWithTag("content").performTouchInput {
      val noUpScope = object : TouchInjectionScope by this {
        override fun up(pointerId: Int) = Unit
      }
      noUpScope.pinchToZoomInBy(IntOffset(5, 5))
    }
    rule.runOnIdle {
      assertThat(state.isZoomedIn).isTrue()
      rule.runOnUiThread {
        dropshots.assertSnapshot(
          rule.activity.takeScreenshotInSoftwareAccelerationMode(),
          testName.methodName + "_[during_zoom]"
        )
      }
    }
  }

  @Test fun disable_zooming_when_View_is_software_accelerated() = runTest {
    lateinit var state: ZoomablePeekOverlayState
    rule.setContent {
      Box(Modifier.fillMaxSize(), Alignment.Center) {
        SoftwareAcceleratedLayout {
          state = rememberZoomablePeekOverlayState()
          Box(
            Modifier
              .size(200.dp)
              .zoomablePeekOverlay(state)
              .background(Color.Yellow)
              .testTag("content")
          )
        }
      }
    }

    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.takePixelCopyScreenshot(), testName.methodName + "_[before_zoom]")
    }

    rule.onNodeWithTag("content").performTouchInput {
      val noUpScope = object : TouchInjectionScope by this {
        override fun up(pointerId: Int) = Unit
      }
      noUpScope.pinchToZoomInBy(IntOffset(5, 5))
    }
    rule.runOnIdle {
      // The content shouldn't have consumed zoom gestures.
      assertThat(state.isZoomedIn).isFalse()
      dropshots.assertSnapshot(rule.activity.takePixelCopyScreenshot(), testName.methodName + "_[during_zoom]")
    }
  }

  @Test fun do_not_restore_state_with_out_of_bounds_zoom() {
    lateinit var state: ZoomablePeekOverlayState
    val recreationTester = ActivityRecreationTester(rule)

    recreationTester.setContent {
      Box(Modifier.fillMaxSize(), Alignment.Center) {
        state = rememberZoomablePeekOverlayState()
        Box(
          Modifier
            .size(200.dp)
            .border(1.dp, Color.Blue)
            .zoomablePeekOverlay(state)
            .background(Color.Yellow)
            .testTag("content")
        )
      }
    }

    rule.onNodeWithTag("content").performTouchInput {
      val noUpScope = object : TouchInjectionScope by this {
        override fun up(pointerId: Int) = Unit
      }
      noUpScope.pinchToZoomInBy(IntOffset(5, 5))
    }
    rule.runOnIdle {
      assertThat(state.zoomableState.contentTransformation.scaleMetadata.userZoom).isGreaterThan(1f)
    }

    recreationTester.recreate()

    rule.runOnIdle {
      assertThat(state.zoomableState.contentTransformation.scaleMetadata.userZoom).isEqualTo(1f)
    }
  }

  @Test fun haptic_feedback() = runTest {
    lateinit var state: ZoomablePeekOverlayState
    val hapticFeedback = RecordingHapticFeedback()

    rule.setContent {
      CompositionLocalProvider(LocalHapticFeedback provides hapticFeedback) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
          state = rememberZoomablePeekOverlayState()
          Box(
            Modifier
              .size(200.dp)
              .zoomablePeekOverlay(state)
              .background(Color.Green)
              .testTag("content")
          )
        }
      }
    }

    rule.onNodeWithTag("content").performTouchInput {
      val noUpScope = object : TouchInjectionScope by this {
        override fun up(pointerId: Int) = Unit
      }
      noUpScope.pinchToZoomInBy(IntOffset(5, 5))
    }
    rule.runOnIdle {
      assertThat(hapticFeedback.performedFeedbacks).isEmpty()
    }

    rule.onNodeWithTag("content").performTouchInput {
      this.cancel()
    }
    rule.waitUntil { !state.isZoomedIn }
    rule.runOnIdle {
      assertThat(hapticFeedback.performedFeedbacks.removeAll()).containsExactly(HapticFeedbackType.GestureEnd)
    }

    rule.onNodeWithTag("content").performTouchInput {
      pinchToZoomInBy(IntOffset(-5, -5))
    }
    rule.runOnIdle {
      assertThat(hapticFeedback.performedFeedbacks.removeAll()).containsExactly(HapticFeedbackType.Reject)
    }
  }
}

@Suppress("DEPRECATION")
private fun Activity.takeScreenshotInSoftwareAccelerationMode(): Bitmap {
  val decorView = window.decorView
  decorView.isDrawingCacheEnabled = true
  return Bitmap.createBitmap(decorView.drawingCache)
}

private fun TouchInjectionScope.pinchToZoomInBy(by: IntOffset) {
  val start0 = center - Offset(5f, 5f)
  val start1 = center + Offset(5f, 5f)
  pinch(
    start0 = start0,
    start1 = start1,
    end0 = start0 - by.toOffset(),
    end1 = start1 + by.toOffset(),
  )
}

private fun Activity.takePixelCopyScreenshot(): Bitmap {
  val latch = CountDownLatch(1)
  var bitmap: Bitmap? = null

  PixelCopy.request(
    /* request = */ PixelCopy.Request.Builder.ofWindow(window).build(),
    /* callbackExecutor = */ Executor(Runnable::run),
  ) { result ->
    try {
      bitmap = result.bitmap
    } finally {
      latch.countDown()
    }
  }

  latch.await()
  return checkNotNull(bitmap) { "PixelCopy did not return a bitmap" }
}

// This was written because disabling HW acceleration on the test activity doesn't seem to be working.
@Composable
private fun SoftwareAcceleratedLayout(content: @Composable () -> Unit) {
  AndroidView(
    factory = { context ->
      object : AbstractComposeView(context) {
        override fun isHardwareAccelerated(): Boolean = false

        @Composable override fun Content() {
          CompositionLocalProvider(LocalView provides this) {
            content()
          }
        }
      }
    },
  )
}

private fun <T> ArrayDeque<T>.removeAll(): List<T> {
  val source = this
  val destination = ArrayList(source)
  source.clear()
  return destination
}
