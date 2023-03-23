package me.saket.telephoto.zoomable

import android.graphics.BitmapFactory
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import com.dropbox.dropshots.Dropshots
import com.dropbox.dropshots.ResultValidator
import com.dropbox.dropshots.ThresholdValidator
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import leakcanary.DetectLeaksAfterTestSuccess.Companion.detectLeaksAfterTestSuccessWrapping
import me.saket.telephoto.zoomable.ZoomableViewportTest.ScrollDirection
import me.saket.telephoto.zoomable.ZoomableViewportTest.ScrollDirection.LeftToRight
import me.saket.telephoto.zoomable.ZoomableViewportTest.ScrollDirection.RightToLeft
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.runner.RunWith

@ExperimentalFoundationApi
@RunWith(TestParameterInjector::class)
class ZoomableViewportTest {
  private val rule = createAndroidComposeRule<ComponentActivity>()
  private val testName = TestName()
  private val dropshots = Dropshots(
    filenameFunc = { it },
    resultValidator = ThresholdValidator(thresholdPercent = 0.01f)
  )

  @get:Rule val rules: RuleChain = RuleChain.outerRule(dropshots)
    .around(testName)
    .detectLeaksAfterTestSuccessWrapping("ActivitiesDestroyed") {
      around(rule)
    }

  @Before
  fun setup() {
    rule.activityRule.scenario.onActivity {
      it.actionBar?.hide()

      // Remove any space occupied by system bars to reduce differences
      // in from screenshots generated on different devices.
      it.window.setDecorFitsSystemWindows(false)
      it.window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }
  }

  @Test fun canary() {
    rule.setContent {
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
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun zoom_in() {
    var finalScale = ScaleFactor.Unspecified

    rule.setContent {
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

    rule.onNodeWithTag("viewport").performTouchInput {
      pinchToZoomBy(visibleSize.center / 2f)
    }
    rule.runOnIdle {
      assertThat(finalScale.scaleX).isEqualTo(2f)
      assertThat(finalScale.scaleY).isEqualTo(2f)
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun retain_transformations_across_state_restorations() {
    val stateRestorationTester = StateRestorationTester(rule)

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

    with(rule.onNodeWithTag("viewport")) {
      performTouchInput {
        pinchToZoomBy(visibleSize.center / 2f)
      }
      performTouchInput {
        swipeLeft(startX = center.x, endX = centerLeft.x)
      }
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }

    stateRestorationTester.emulateSavedInstanceStateRestore()

    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun various_alignments_and_scales(
    @TestParameter alignment: AlignmentParam,
    @TestParameter contentScale: ContentScaleParam,
    @TestParameter imageAsset: ImageAssetParam,
  ) {
    rule.setContent {
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
            assetName = imageAsset.assetName,
          )
        }
      }
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName)
    }

    with(rule.onNodeWithTag("viewport")) {
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
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_zoomed")
    }

    with(rule.onNodeWithTag("viewport")) {
      performTouchInput {
        swipeLeft(startX = center.x, endX = centerLeft.x)
      }
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_zoomed_panned")
    }
  }

  @Test fun updating_of_content_alignment() {
    var contentAlignment by mutableStateOf(Alignment.BottomCenter)

    rule.setContent {
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
    dropshots.assertSnapshot(rule.activity, testName.methodName + "_before_updating_alignment")

    contentAlignment = Alignment.TopCenter
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_after_updating_alignment")
    }
  }

  @Test fun updating_of_content_scale() {
    var contentScale by mutableStateOf(ContentScale.Crop)

    rule.setContent {
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
    dropshots.assertSnapshot(rule.activity, testName.methodName + "_before_updating_scale")

    contentScale = ContentScale.Inside
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_after_updating_scale")
    }
  }

  @Test fun pager_can_be_scrolled_when_content_is_fully_zoomed_out_and_cannot_pan(
    @TestParameter scrollDirection: ScrollDirection
  ) {
    val assetNames = listOf(
      "forest_fox_1000.jpg",
      "fox_1500.jpg",
      "cat_1920.jpg"
    )

    rule.setContent {
      ScreenScaffold {
        HorizontalPager(
          modifier = Modifier.testTag("pager"),
          state = rememberPagerState(initialPage = 1),
          pageCount = assetNames.size
        ) { pageNum ->
          val viewportState = rememberZoomableViewportState()
          ZoomableViewport(
            state = viewportState,
            contentScale = ContentScale.Fit,
          ) {
            ImageAsset(
              viewportState = viewportState,
              assetName = assetNames[pageNum]
            )
          }
        }
      }
    }

    rule.onNodeWithTag("pager").performTouchInput {
      swipeWithVelocity(scrollDirection)
    }
    rule.mainClock.advanceTimeByFrame()
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun pager_should_not_scroll_when_content_is_zoomed_in_and_can_pan(
    @TestParameter scrollDirection: ScrollDirection
  ) {
    val assetNames = listOf(
      "forest_fox_1000.jpg",
      "cat_1920.jpg",
      "fox_1500.jpg"
    )

    rule.setContent {
      ScreenScaffold {
        HorizontalPager(
          modifier = Modifier.testTag("pager"),
          state = rememberPagerState(initialPage = 1),
          pageCount = assetNames.size
        ) { pageNum ->
          val viewportState = rememberZoomableViewportState(maxZoomFactor = 2f)
          ZoomableViewport(
            state = viewportState,
            contentScale = ContentScale.Fit,
          ) {
            ImageAsset(
              viewportState = viewportState,
              assetName = assetNames[pageNum]
            )
          }
        }
      }
    }

    with(rule.onNodeWithTag("pager")) {
      performTouchInput {
        pinchToZoomBy(visibleSize.center / 2f)
      }
      performTouchInput {
        swipeWithVelocity(scrollDirection)
      }
    }

    rule.mainClock.advanceTimeByFrame()
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun pager_should_scroll_when_content_is_zoomed_in_but_cannot_pan(
    @TestParameter scrollDirection: ScrollDirection
  ) {
    val assetNames = listOf(
      "forest_fox_1000.jpg",
      "cat_1920.jpg",
      "fox_1500.jpg"
    )

    rule.setContent {
      ScreenScaffold {
        HorizontalPager(
          modifier = Modifier.testTag("pager"),
          state = rememberPagerState(initialPage = 1),
          pageCount = assetNames.size
        ) { pageNum ->
          val viewportState = rememberZoomableViewportState(maxZoomFactor = 1.5f)
          ZoomableViewport(
            state = viewportState,
            contentScale = ContentScale.Fit,
          ) {
            ImageAsset(
              viewportState = viewportState,
              assetName = assetNames[pageNum]
            )
          }
        }
      }
    }

    with(rule.onNodeWithTag("pager")) {
      performTouchInput {
        pinchToZoomBy(visibleSize.center / 2f)
      }
      // First swipe will fully pan the content to its edge.
      // Second swipe should scroll the pager.
      performTouchInput {
        swipe(scrollDirection)
      }
      performTouchInput {
        swipe(scrollDirection)
      }
    }

    rule.mainClock.advanceTimeByFrame()
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun reset_transformations_when_content_is_changed() {
    val maxZoomFactor = 2f
    var imageScale = ScaleFactor.Unspecified
    var assetName by mutableStateOf("fox_1500.jpg")

    rule.setContent {
      ScreenScaffold {
        val viewportState = rememberZoomableViewportState(maxZoomFactor = maxZoomFactor)
        ZoomableViewport(
          modifier = Modifier.testTag("viewport"),
          state = viewportState,
          contentScale = ContentScale.Fit,
        ) {
          ImageAsset(
            viewportState = viewportState,
            assetName = assetName
          )
        }

        LaunchedEffect(viewportState.contentTransformation) {
          imageScale = viewportState.contentTransformation.scale
        }
      }
    }

    rule.onNodeWithTag("viewport").performTouchInput {
      doubleClick()
    }
    rule.runOnIdle {
      assertThat(imageScale).isEqualTo(ScaleFactor(maxZoomFactor, maxZoomFactor))
    }

    assetName = "cat_1920.jpg"

    rule.runOnIdle {
      assertThat(imageScale).isEqualTo(ScaleFactor(1f, 1f))
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun on_click_works() {
    var onClickCalled = false
    var onLongClickCalled = false

    rule.setContent {
      val state = rememberZoomableViewportState()
      ZoomableViewport(
        state = state,
        contentScale = ContentScale.Inside,
        onClick = { onClickCalled = true },
        onLongClick = { onLongClickCalled = true }
      ) {
        Box(
          Modifier
            .testTag("content")
            .fillMaxSize()
            .onSizeChanged {
              state.setContentLocation(
                ZoomableContentLocation.fitInsideAndCenterAligned(it.toSize())
              )
            }
        )
      }
    }

    rule.onNodeWithTag("content").performClick()
    rule.runOnIdle {
      // Clicks are delayed until they're confirmed to not be double clicks.
      assertThat(onClickCalled).isFalse()
    }

    rule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong())
    rule.runOnIdle {
      assertThat(onClickCalled).isTrue()
      assertThat(onLongClickCalled).isFalse()
    }

    rule.onNodeWithTag("content").performTouchInput { longClick() }
    rule.runOnIdle {
      assertThat(onLongClickCalled).isTrue()
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
        ZoomableContentLocation.fitInsideAndCenterAligned(painter.intrinsicSize)
      )
    }

    Image(
      modifier = Modifier
        .fillMaxSize()
        .applyTransformation(viewportState.contentTransformation),
      painter = painter,
      contentDescription = null,
      contentScale = ContentScale.Inside,
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
    Fill(ContentScale.FillBounds),
  }

  @Suppress("unused")
  enum class ImageAssetParam(val assetName: String) {
    SmallerThanViewport("fox_250.jpg"),
    LargerThanViewport("cat_1920.jpg")
  }

  @Suppress("unused")
  enum class ScrollDirection {
    RightToLeft,
    LeftToRight
  }
}

@OptIn(ExperimentalFoundationApi::class)
private fun TouchInjectionScope.swipeWithVelocity(
  direction: ScrollDirection,
  velocity: Float = 5_000f,
) {
  when (direction) {
    RightToLeft -> swipeWithVelocity(
      start = centerRight,
      end = center,
      endVelocity = velocity,
    )

    LeftToRight -> swipeWithVelocity(
      start = centerLeft,
      end = center,
      endVelocity = velocity,
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
private fun TouchInjectionScope.swipe(
  direction: ScrollDirection
) {
  when (direction) {
    RightToLeft -> swipeLeft(
      startX = centerRight.x,
      endX = centerLeft.x,
    )

    LeftToRight -> swipeRight(
      startX = centerLeft.x,
      endX = centerRight.x,
    )
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
private fun assetPainter(assetName: String): Painter {
  val context = LocalContext.current
  return remember(assetName) {
    context.assets.open(assetName).use { stream ->
      BitmapPainter(BitmapFactory.decodeStream(stream).asImageBitmap())
    }
  }
}

private fun ThresholdValidator(thresholdPercent: Float): ResultValidator =
  ThresholdValidator(threshold = thresholdPercent / 100)
