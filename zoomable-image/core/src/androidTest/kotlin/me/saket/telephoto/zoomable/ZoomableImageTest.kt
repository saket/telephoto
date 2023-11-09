@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.zoomable

import android.graphics.BitmapFactory
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
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
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import com.dropbox.dropshots.Dropshots
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.util.CiScreenshotValidator
import me.saket.telephoto.util.prepareForScreenshotTest
import me.saket.telephoto.util.waitUntil
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
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalFoundationApi::class)
@RunWith(TestParameterInjector::class)
class ZoomableImageTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val timeout = Timeout.seconds(10)!!
  @get:Rule val testName = TestName()

  private val screenshotValidator = CiScreenshotValidator(
    context = { rule.activity },
    tolerancePercentOnLocal = 0f,
    tolerancePercentOnCi = 0.01f,
  )
  @get:Rule val dropshots = Dropshots(
    filenameFunc = { it },
    resultValidator = screenshotValidator
  )

  @Before
  fun setup() {
    rule.activityRule.scenario.onActivity {
      it.prepareForScreenshotTest()
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
    @TestParameter subSamplingStatus: SubSamplingStatus,
    @TestParameter canZoom: CanZoomParam,
  ) {
    val stateRestorationTester = StateRestorationTester(rule)
    val isPlaceholderVisible = MutableStateFlow(false)
    lateinit var state: ZoomableImageState

    stateRestorationTester.setContent {
      val imageSource = ZoomableImageSource.asset("fox_1500.jpg", subSample = subSamplingStatus.enabled).let {
        if (placeholderParam.canBeUsed) {
          it.withPlaceholder(assetPainter("fox_250.jpg"), isPlaceholderVisible)
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
        state = rememberZoomableImageState(
          rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 5f))
        ).also {
          state = it
        },
      )
    }

    rule.waitUntil(5.seconds) { state.isImageDisplayed }
    if (canZoom.canZoom) {
      with(rule.onNodeWithTag("image")) {
        performTouchInput {
          doubleClick()
        }
        performTouchInput {
          swipeLeft(startX = center.x, endX = centerLeft.x)
        }
      }
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_before_state_restoration")
    }

    isPlaceholderVisible.value = placeholderParam.canBeUsed
    stateRestorationTester.emulateSavedInstanceStateRestore()

    if (placeholderParam.canBeUsed) {
      rule.waitUntil(5.seconds) { state.isPlaceholderDisplayed }
      rule.runOnIdle {
        dropshots.assertSnapshot(rule.activity, testName.methodName + "_placeholder_after_state_restoration")
      }
    }

    isPlaceholderVisible.value = false
    rule.waitUntil(5.seconds) { state.isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_full_image_after_state_restoration")
    }
  }

  @Test fun various_image_sizes_and_alignments(
    @TestParameter alignment: AlignmentParam,
    @TestParameter contentScale: ContentScaleParam,
    @TestParameter imageAsset: ImageAssetParam,
    @TestParameter layoutSize: LayoutSizeParam,
    @TestParameter subSamplingStatus: SubSamplingStatus,
  ) {
    screenshotValidator.tolerancePercentOnCi = 0.18f
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
      dropshots.assertSnapshot(rule.activity)
    }

    rule.onNodeWithTag("image").performTouchInput {
      val by = visibleSize.center / 2f
      pinch(
        start0 = centerLeft,
        start1 = centerLeft,
        end0 = centerLeft - by.toOffset(),
        end1 = centerLeft + by.toOffset(),
      )
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

  @Test fun rtl_layout_direction() {
    screenshotValidator.tolerancePercentOnCi = 0.18f
    var isImageDisplayed = false

    rule.setContent {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val state = rememberZoomableImageState()
        isImageDisplayed = state.isImageDisplayed && state.zoomableState.contentTransformation.isSpecified

        ZoomableImage(
          modifier = Modifier
            .fillMaxSize()
            .testTag("image"),
          image = ZoomableImageSource.asset("fox_1500.jpg", subSample = true),
          contentDescription = null,
          state = state,
        )
      }
    }

    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }

    rule.onNodeWithTag("image").performTouchInput {
      doubleClick()
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
        state = rememberPagerState(initialPage = 1, pageCount = { assetNames.size }),
      ) { pageNum ->
        ZoomableImage(
          modifier = Modifier.fillMaxSize(),
          image = ZoomableImageSource.asset(assetNames[pageNum], subSample = false),
          state = rememberZoomableImageState(
            rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 1f))
          ),
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
        state = rememberPagerState(initialPage = 1, pageCount = { assetNames.size }),
      ) { pageNum ->
        ZoomableImage(
          modifier = Modifier.fillMaxSize(),
          image = ZoomableImageSource.asset(assetNames[pageNum], subSample = false),
          state = rememberZoomableImageState(rememberZoomableState()),
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
        state = rememberPagerState(initialPage = 1, pageCount = { assetNames.size }),
      ) { pageNum ->
        ZoomableImage(
          modifier = Modifier.fillMaxSize(),
          image = ZoomableImageSource.asset(assetNames[pageNum], subSample = false),
          state = rememberZoomableImageState(
            rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 1.5f))
          ),
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
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = maxZoomFactor)
      )
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = ZoomableImageSource.asset(assetName, subSample = false),
        contentDescription = null,
        state = rememberZoomableImageState(zoomableState),
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
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = maxZoomFactor)
      )
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        state = rememberZoomableImageState(zoomableState),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        contentDescription = null,
      )

      LaunchedEffect(zoomableState.contentTransformation) {
        imageScale = zoomableState.contentTransformation.scale
      }
      LaunchedEffect(resetTriggers) {
        resetTriggers.receive()
        zoomableState.resetZoom(withAnimation = false)
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

  @Test fun zoom_fraction_is_correctly_calculated(
    @TestParameter scale: ContentScaleParam
  ) {
    var state: ZoomableImageState? = null
    fun zoomFraction() = state!!.zoomableState.zoomFraction

    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 3f)
      )
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        state = rememberZoomableImageState(zoomableState).also { state = it },
        contentScale = scale.value,
        contentDescription = null,
      )
    }

    rule.waitUntil(5.seconds) { state!!.isImageDisplayed }

    val isImageStretchedToFill = when (scale) {
      ContentScaleParam.Crop -> true
      ContentScaleParam.Fit -> false
      ContentScaleParam.Inside -> false
      ContentScaleParam.Fill -> true
    }

    if (isImageStretchedToFill) {
      rule.runOnIdle {
        assertThat(zoomFraction()).isEqualTo(1f)
      }
    } else {
      rule.runOnIdle {
        assertThat(zoomFraction()).isEqualTo(0f)
      }

      rule.onNodeWithTag("image").performTouchInput {
        pinchToZoomBy(IntOffset(0, 5))
      }
      rule.runOnIdle {
        assertThat(zoomFraction()).isWithin(0.1f).of(0.6f)
      }

      rule.onNodeWithTag("image").performTouchInput {
        doubleClick()
      }
      rule.runOnIdle {
        assertThat(zoomFraction()).isEqualTo(1f)
      }
    }
  }

  @Test fun click_listeners_work() {
    var clicksCount = 0
    var longClicksCount = 0
    val composableTag = "zoomable_image"

    rule.setContent {
      val state = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      )
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag(composableTag),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        contentDescription = null,
        state = rememberZoomableImageState(state),
        contentScale = ContentScale.Inside,
        onClick = { ++clicksCount },
        onLongClick = { ++longClicksCount }
      )
    }

    rule.onNodeWithTag(composableTag).performClick()
    rule.runOnIdle {
      // Clicks are delayed until they're confirmed to not be double clicks
      // so make sure that onClick does not get called prematurely.
      assertThat(clicksCount).isEqualTo(0)
    }
    rule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong())
    rule.runOnIdle {
      assertThat(clicksCount).isEqualTo(1)
      assertThat(longClicksCount).isEqualTo(0)
    }

    rule.onNodeWithTag(composableTag).performTouchInput { longClick() }
    rule.runOnIdle {
      assertThat(clicksCount).isEqualTo(1)
      assertThat(longClicksCount).isEqualTo(1)
    }

    // Regression testing for https://github.com/saket/telephoto/issues/18.
    // Perform double click to zoom in and make sure click listeners still work
    rule.onNodeWithTag(composableTag).performTouchInput { doubleClick() }
    rule.waitForIdle()

    rule.onNodeWithTag(composableTag).performClick()
    rule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong())
    rule.runOnIdle {
      assertThat(clicksCount).isEqualTo(2)
    }

    rule.onNodeWithTag(composableTag).performTouchInput { longClick() }
    rule.runOnIdle {
      assertThat(longClicksCount).isEqualTo(2)
    }

    // Perform double click to zoom out and make sure click listeners still work
    rule.onNodeWithTag(composableTag).performTouchInput { doubleClick() }
    rule.waitForIdle()

    rule.onNodeWithTag(composableTag).performClick()
    rule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong())
    rule.runOnIdle {
      assertThat(clicksCount).isEqualTo(3)
    }

    rule.onNodeWithTag(composableTag).performTouchInput { longClick() }
    rule.runOnIdle {
      assertThat(longClicksCount).isEqualTo(3)
    }
  }

  @Test fun quick_zooming_works() {
    val maxZoomFactor = 2f
    var currentZoom = 0f

    rule.setContent {
      val state = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = maxZoomFactor)
      )
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
      quickZoom()
    }
    rule.runOnIdle {
      // Zoom should never cross the max zoom even if the gesture above over-zooms.
      assertThat(currentZoom).isEqualTo(maxZoomFactor)
    }
  }

  @Test fun double_click_should_toggle_zoom() {
    var zoomFraction: Float? = null

    rule.setContent {
      val state = rememberZoomableState(
        zoomSpec = ZoomSpec()
      )
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

      LaunchedEffect(state.zoomFraction) {
        zoomFraction = state.zoomFraction
      }
    }

    rule.onNodeWithTag("zoomable").performTouchInput { doubleClick() }
    rule.runOnIdle {
      assertThat(zoomFraction).isEqualTo(1f)
    }

    rule.onNodeWithTag("zoomable").performTouchInput { doubleClick() }
    rule.runOnIdle {
      assertThat(zoomFraction).isEqualTo(0f)
    }
  }

  @Test fun gestures_are_ignored_when_gestures_are_disabled() {
    var state: ZoomableImageState? = null
    fun zoomFraction() = state!!.zoomableState.zoomFraction

    var onClickCalled = false
    var onLongClickCalled = false

    rule.setContent {
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        state = rememberZoomableImageState(
          rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 5f))
        ).also { state = it },
        contentDescription = null,
        gesturesEnabled = false,
        onClick = { onClickCalled = true },
        onLongClick = { onLongClickCalled = true },
      )
    }

    rule.onNodeWithTag("image").run {
      performTouchInput {
        pinchToZoomBy(visibleSize.center / 2f)
      }
      performTouchInput {
        doubleClick()
      }
      performTouchInput {
        quickZoom()
      }
      performTouchInput {
        swipe(LeftToRight)
      }
      performTouchInput {
        swipeWithVelocity(RightToLeft)
      }
    }

    rule.runOnIdle {
      assertThat(zoomFraction()).isEqualTo(0f)
    }

    rule.onNodeWithTag("image").performTouchInput { longClick() }
    rule.runOnIdle {
      assertThat(onLongClickCalled).isTrue()
    }

    rule.onNodeWithTag("image").performTouchInput { click() }
    rule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong())
    rule.runOnIdle {
      assertThat(onClickCalled).isTrue()
    }
  }

  // Regression test for https://github.com/saket/telephoto/issues/33
  @Test fun panning_in_reverse_works_after_image_is_panned_to_the_edge() {
    var isImageDisplayed = false

    rule.setContent {
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = ZoomableImageSource.asset("cat_1920.jpg", subSample = false),
        contentDescription = null,
        state = rememberZoomableImageState(
          rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 2f))
        ).also {
          isImageDisplayed = it.isImageDisplayed
        },
      )
    }

    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.onNodeWithTag("image").performTouchInput {
      doubleClick(position = Offset.Zero)
    }

    // Scenario: when the image has reached its edge where it can't be panned any further,
    // changing the direction of the swipe gesture should pan the image in the opposite direction.
    rule.onNodeWithTag("image").performTouchInput {
      val noUpScope = object : TouchInjectionScope by this {
        override fun up(pointerId: Int) = Unit
      }
      noUpScope.swipeDown(startY = top, endY = centerY)

      val noDownScope = object : TouchInjectionScope by this {
        override fun down(pointerId: Int, position: Offset) = Unit
      }
      noDownScope.swipeUp(startY = centerY, endY = top)
    }

    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
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
  enum class CanZoomParam(val canZoom: Boolean) {
    CanZoom(canZoom = true),
    CanNotZoom(canZoom = false),
  }

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

private fun TouchInjectionScope.quickZoom() {
  val doubleTapMinTimeMillis = 40L // From LocalViewConfiguration.current.doubleTapMinTimeMillis.

  click(center)
  advanceEventTime(doubleTapMinTimeMillis + 2)
  swipeDown(startY = centerY, endY = bottom, durationMillis = 1_000)
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
      @Composable
      override fun resolve(canvasSize: Flow<Size>): ResolveResult {
        return ResolveResult(
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

@Composable
private fun ZoomableImageSource.withPlaceholder(
  placeholder: Painter,
  isPlaceholderVisible: MutableStateFlow<Boolean>
): ZoomableImageSource {
  val delegate = this
  return remember(delegate, placeholder, isPlaceholderVisible) {
    object : ZoomableImageSource {
      @Composable
      override fun resolve(canvasSize: Flow<Size>): ResolveResult {
        val showPlaceholder by isPlaceholderVisible.collectAsState()
        return when {
          showPlaceholder -> ResolveResult(delegate = null, placeholder = placeholder)
          else -> delegate.resolve(canvasSize).copy(placeholder = placeholder)
        }
      }
    }
  }
}
