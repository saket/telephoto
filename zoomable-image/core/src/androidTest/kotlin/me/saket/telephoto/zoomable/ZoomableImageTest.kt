package me.saket.telephoto.zoomable

import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import com.dropbox.dropshots.Dropshots
import com.dropbox.dropshots.ResultValidator
import com.dropbox.dropshots.ThresholdValidator
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.zoomable.ZoomableImageSource.ResolveResult
import me.saket.telephoto.zoomable.ZoomableImageTest.ScrollDirection
import me.saket.telephoto.zoomable.ZoomableImageTest.ScrollDirection.LeftToRight
import me.saket.telephoto.zoomable.ZoomableImageTest.ScrollDirection.RightToLeft
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalFoundationApi::class)
@RunWith(TestParameterInjector::class)
class ZoomableImageTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val timeout = Timeout.seconds(10)!!
  @get:Rule val testName = TestName()
  @get:Rule val dropshots = Dropshots(
    filenameFunc = { it },
    resultValidator = ThresholdValidator(thresholdPercent = 0.01f)
  )

  @Before
  fun setup() {
    rule.activityRule.scenario.onActivity {
      it.actionBar?.hide()
      it.window.setBackgroundDrawable(ColorDrawable(0xFF1C1A25.toInt()))

      // Remove any space occupied by system bars to reduce differences
      // in from screenshots generated on different devices.
      it.window.setDecorFitsSystemWindows(false)
      it.window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }
  }

  @Test fun canary() {
    rule.setContent {
      ZoomableImage(
        modifier = Modifier.fillMaxSize(),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        contentDescription = null,
      )
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun zoom_in() {
    var finalScale = ScaleFactor.Unspecified

    rule.setContent {
      val zoomableState = rememberZoomableState()
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        state = rememberZoomableImageState(zoomableState),
        contentDescription = null,
      )
      LaunchedEffect(zoomableState.contentTransformation) {
        finalScale = zoomableState.contentTransformation.scale
      }
    }

    rule.onNodeWithTag("image").performTouchInput {
      pinchToZoomBy(visibleSize.center / 2f)
    }
    rule.runOnIdle {
      assertThat(finalScale.scaleX).isEqualTo(2f)
      assertThat(finalScale.scaleY).isEqualTo(2f)
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun retain_transformations_across_state_restorations(
    @TestParameter placeholderParam: UsePlaceholderParam,
    @TestParameter subSamplingStatus: SubSamplingStatus
  ) {
    val stateRestorationTester = StateRestorationTester(rule)
    var isImageDisplayed = false

    stateRestorationTester.setContent {
      val imageSource = ZoomableImageSource.asset("fox_1500.jpg", subSample = subSamplingStatus.enabled).let {
        if (placeholderParam.canBeUsed) {
          it.withPlaceholder(
            placeholder = assetPainter("fox_250.jpg"),
            // Bug test: giving an expectedSize value that does not match the actual size was
            // breaking state restoration because the state would get restored w.r.t this size.
            expectedSize = Size(50f, 50f)
          )
        } else {
          it
        }
      }
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = imageSource,
        contentDescription = null,
        state = rememberZoomableImageState(rememberZoomableState(maxZoomFactor = 5f)).also {
          isImageDisplayed = it.isImageDisplayed
        },
      )
    }

    rule.waitUntil(5.seconds) { isImageDisplayed }
    with(rule.onNodeWithTag("image")) {
      performTouchInput {
        doubleClick()
      }
      performTouchInput {
        swipeLeft(startX = center.x, endX = centerLeft.x)
      }
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_before_state_restoration")
    }

    stateRestorationTester.emulateSavedInstanceStateRestore()

    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_after_state_restoration")
    }
  }

  @Test fun various_image_sizes_and_alignments(
    @TestParameter alignment: AlignmentParam,
    @TestParameter contentScale: ContentScaleParam,
    @TestParameter imageAsset: ImageAssetParam,
    @TestParameter layoutSize: LayoutSizeParam,
    @TestParameter subSamplingStatus: SubSamplingStatus,
  ) {
    var isImageDisplayed = false

    rule.setContent {
      val state = rememberZoomableImageState()
      isImageDisplayed = state.isImageDisplayed && state.zoomableState.contentTransformation.isSpecified

      ZoomableImage(
        modifier = Modifier
          .then(layoutSize.modifier)
          .testTag("image"),
        image = ZoomableImageSource.asset(imageAsset.assetName, subSample = subSamplingStatus.enabled),
        contentDescription = null,
        state = state,
        contentScale = contentScale.value,
        alignment = alignment.value,
      )
    }

    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName)
    }

    with(rule.onNodeWithTag("image")) {
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
    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_zoomed")
    }

    with(rule.onNodeWithTag("image")) {
      performTouchInput {
        swipeLeft(startX = center.x, endX = centerLeft.x)
      }
    }
    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_zoomed_panned")
    }
  }

  @Test fun updating_of_content_alignment() {
    var contentAlignment by mutableStateOf(Alignment.BottomCenter)

    rule.setContent {
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .fillMaxSize(),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        contentScale = ContentScale.Fit,
        alignment = contentAlignment,
        contentDescription = null,
      )
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
      ZoomableImage(
        modifier = Modifier.fillMaxSize(),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        contentScale = contentScale,
        contentDescription = null,
      )
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
      HorizontalPager(
        modifier = Modifier.testTag("pager"),
        state = rememberPagerState(initialPage = 1),
        pageCount = assetNames.size
      ) { pageNum ->
        ZoomableImage(
          modifier = Modifier.fillMaxSize(),
          image = ZoomableImageSource.asset(assetNames[pageNum], subSample = false),
          state = rememberZoomableImageState(rememberZoomableState(maxZoomFactor = 1f)),
          contentScale = ContentScale.Fit,
          contentDescription = null,
        )
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
      HorizontalPager(
        modifier = Modifier.testTag("pager"),
        state = rememberPagerState(initialPage = 1),
        pageCount = assetNames.size
      ) { pageNum ->
        ZoomableImage(
          modifier = Modifier.fillMaxSize(),
          image = ZoomableImageSource.asset(assetNames[pageNum], subSample = false),
          state = rememberZoomableImageState(rememberZoomableState(maxZoomFactor = 2f)),
          contentScale = ContentScale.Fit,
          contentDescription = null,
        )
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
      HorizontalPager(
        modifier = Modifier.testTag("pager"),
        state = rememberPagerState(initialPage = 1),
        pageCount = assetNames.size
      ) { pageNum ->
        ZoomableImage(
          modifier = Modifier.fillMaxSize(),
          image = ZoomableImageSource.asset(assetNames[pageNum], subSample = false),
          state = rememberZoomableImageState(rememberZoomableState(maxZoomFactor = 1.5f)),
          contentScale = ContentScale.Fit,
          contentDescription = null,
        )
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

  @Test fun do_not_auto_reset_transformations_when_content_is_changed() {
    val maxZoomFactor = 2f
    var imageScale = ScaleFactor.Unspecified
    var assetName by mutableStateOf("fox_1500.jpg")

    rule.setContent {
      val zoomableState = rememberZoomableState(maxZoomFactor = maxZoomFactor)
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = ZoomableImageSource.asset(assetName, subSample = false),
        contentDescription = null,
        state = rememberZoomableImageState(zoomableState),
        contentScale = ContentScale.Fit,
      )

      LaunchedEffect(zoomableState.contentTransformation) {
        imageScale = zoomableState.contentTransformation.scale
      }
    }

    rule.onNodeWithTag("image").performTouchInput {
      doubleClick()
    }
    rule.runOnIdle {
      assertThat(imageScale).isEqualTo(ScaleFactor(maxZoomFactor, maxZoomFactor))
    }

    // It sounds weird that changing the image does not auto-reset transformations,
    // but the idea is that in the future it should be possible to load a low-quality
    // preview as a placeholder before loading the full image.
    assetName = "cat_1920.jpg"

    rule.runOnIdle {
      assertThat(imageScale).isEqualTo(ScaleFactor(2f, 2f))
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun reset_content_transformations() {
    val maxZoomFactor = 2f
    var imageScale = ScaleFactor.Unspecified
    val resetTriggers = Channel<Unit>()

    rule.setContent {
      val zoomableState = rememberZoomableState(maxZoomFactor = maxZoomFactor)
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        state = rememberZoomableImageState(zoomableState),
        contentScale = ContentScale.Fit,
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        contentDescription = null,
      )

      LaunchedEffect(zoomableState.contentTransformation) {
        imageScale = zoomableState.contentTransformation.scale
      }
      LaunchedEffect(resetTriggers) {
        resetTriggers.receive()
        zoomableState.resetZoomImmediately()
      }
    }

    rule.onNodeWithTag("image").performTouchInput {
      doubleClick()
    }
    rule.runOnIdle {
      assertThat(imageScale).isEqualTo(ScaleFactor(maxZoomFactor, maxZoomFactor))
    }

    resetTriggers.trySend(Unit)
    rule.runOnIdle {
      assertThat(imageScale).isEqualTo(ScaleFactor(1f, 1f))
    }
  }

  @Test fun zoom_fraction_is_correctly_calculated() {
    var zoomFraction: Float? = null

    rule.setContent {
      val zoomableState = rememberZoomableState(maxZoomFactor = 3f)
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        state = rememberZoomableImageState(zoomableState),
        contentScale = ContentScale.Fit,
        contentDescription = null,
      )

      LaunchedEffect(zoomableState.zoomFraction) {
        zoomFraction = zoomableState.zoomFraction
      }
    }

    rule.runOnIdle {
      assertThat(zoomFraction).isEqualTo(0f)
    }

    rule.onNodeWithTag("image").performTouchInput {
      pinchToZoomBy(IntOffset(0, 5))
    }
    rule.runOnIdle {
      assertThat(zoomFraction).isWithin(0.1f).of(0.6f)
    }

    rule.onNodeWithTag("image").performTouchInput {
      doubleClick()
    }
    rule.runOnIdle {
      assertThat(zoomFraction).isEqualTo(1f)
    }
  }

  @Test fun click_listeners_work() {
    var onClickCalled = false
    var onLongClickCalled = false

    rule.setContent {
      val state = rememberZoomableState(maxZoomFactor = 1f)
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("zoomable_image"),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        contentDescription = null,
        state = rememberZoomableImageState(state),
        contentScale = ContentScale.Inside,
        onClick = { onClickCalled = true },
        onLongClick = { onLongClickCalled = true }
      )
    }

    rule.onNodeWithTag("zoomable_image").performClick()
    rule.runOnIdle {
      // Clicks are delayed until they're confirmed to not be double clicks
      // so make sure that onClick does not get called prematurely.
      assertThat(onClickCalled).isFalse()
    }
    rule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong())
    rule.runOnIdle {
      assertThat(onClickCalled).isTrue()
      assertThat(onLongClickCalled).isFalse()
    }

    rule.onNodeWithTag("zoomable_image").performTouchInput { longClick() }
    rule.runOnIdle {
      assertThat(onLongClickCalled).isTrue()
    }
  }

  @Test fun quick_zooming_works() {
    val maxZoomFactor = 2f
    var currentZoom = 0f

    rule.setContent {
      val state = rememberZoomableState(maxZoomFactor = maxZoomFactor)
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("zoomable"),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        contentDescription = null,
        state = rememberZoomableImageState(state),
        onClick = { error("click listener should not get called") },
        onLongClick = { error("long click listener should not get called") },
      )

      LaunchedEffect(state.contentTransformation) {
        currentZoom = state.contentTransformation.scale.scaleY
      }
    }

    rule.onNodeWithTag("zoomable").performTouchInput {
      click(center)
      advanceEventTime(42)
      swipeDown(startY = centerY, endY = bottom, durationMillis = 1_000)
    }
    rule.runOnIdle {
      // Zoom should never cross the max zoom even if the gesture above over-zooms.
      assertThat(currentZoom).isEqualTo(maxZoomFactor)
    }
  }

  @Suppress("unused")
  enum class LayoutSizeParam(val modifier: Modifier) {
    FillMaxSize(Modifier.fillMaxSize()),
    WrapContent(Modifier.wrapContentSize()),
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
    SmallerThanLayoutSize("fox_250.jpg"),
    LargerThanLayoutSize("cat_1920.jpg")
  }

  @Suppress("unused")
  enum class SubSamplingStatus(val enabled: Boolean) {
    SubSamplingEnabled(enabled = true),
    SubSamplingDisabled(enabled = false),
  }

  @Suppress("unused")
  enum class UsePlaceholderParam(val canBeUsed: Boolean) {
    PlaceholderEnabled(canBeUsed = true),
    PlaceholderDisabled(canBeUsed = false),
  }

  @Suppress("unused")
  enum class ScrollDirection {
    RightToLeft,
    LeftToRight
  }
}

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

@Composable
private fun ZoomableImageSource.Companion.asset(assetName: String, subSample: Boolean): ZoomableImageSource {
  return remember(assetName) {
    object : ZoomableImageSource {
      @Composable override fun resolve(canvasSize: Flow<Size>) =
        if (subSample) {
          ZoomableImageSource.RequiresSubSampling(
            source = SubSamplingImageSource.asset(assetName),
            placeholder = null,
            expectedSize = Size.Unspecified,
          )
        } else {
          ZoomableImageSource.Generic(assetPainter(assetName))
        }
    }
  }
}

@Composable
private fun ZoomableImageSource.withPlaceholder(placeholder: Painter, expectedSize: Size): ZoomableImageSource {
  val delegate = this
  return remember(delegate, placeholder) {
    object : ZoomableImageSource {
      @Composable
      override fun resolve(canvasSize: Flow<Size>): ResolveResult {
        val canUseDelegate by produceState(initialValue = false) {
          delay(200)
          value = true
        }
        if (canUseDelegate) {
          return when (val delegated = delegate.resolve(canvasSize)) {
            is ZoomableImageSource.Generic -> delegated.copy(placeholder = placeholder)
            is ZoomableImageSource.RequiresSubSampling -> delegated.copy(
              placeholder = placeholder,
              expectedSize = expectedSize
            )
          }
        } else {
          return ZoomableImageSource.Generic(
            image = null,
            placeholder = placeholder
          )
        }
      }
    }
  }
}

internal fun ThresholdValidator(thresholdPercent: Float): ResultValidator =
  ThresholdValidator(threshold = thresholdPercent / 100)

private fun AndroidComposeTestRule<*, *>.waitUntil(timeout: Duration, condition: () -> Boolean) {
  this.waitUntil(timeoutMillis = timeout.inWholeMilliseconds, condition)
}
