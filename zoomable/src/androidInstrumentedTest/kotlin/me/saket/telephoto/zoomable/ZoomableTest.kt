package me.saket.telephoto.zoomable

import android.view.ViewConfiguration
import androidx.compose.animation.core.SnapSpec
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isTrue
import com.dropbox.dropshots.Dropshots
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.runTest
import leakcanary.LeakAssertions
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.util.CiScreenshotValidator
import me.saket.telephoto.util.ScreenshotTestActivity
import me.saket.telephoto.util.assetPainter
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.spatial.SpatialOffset
import me.saket.telephoto.zoomable.spatial.SpatialRect
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
@OptIn(ExperimentalTelephotoApi::class)
class ZoomableTest {
  @get:Rule val rule = createAndroidComposeRule<ScreenshotTestActivity>()
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

  @After
  fun tearDown() {
    LeakAssertions.assertNoLeaks()
  }

  @Test fun canary() {
    rule.setContent {
      Box(
        Modifier
          .padding(16.dp)
          .fillMaxSize()
          .zoomable(rememberZoomableState())
          .background(
            Brush.linearGradient(
              colors = listOf(
                Color(0xFF504E9A),
                Color(0xFF772E6A),
                Color(0xFF79192C),
                Color(0xFF560D1A),
              ),
            )
          )
      )
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun initial_zoom_respects_zoom_spec_constraints() {
    lateinit var state: ZoomableState
    val contentSize = 120.dp

    rule.setContent {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // The ruler marks how large the child should render when initial zoom constraints are respected.
        Box(
          Modifier
            .size(contentSize * 2f)
            .background(Color(0x1A504E9A))
            .drawBehind {
              val gridColor = Color(0x66B9B6FF)
              drawLine(
                color = gridColor,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
              )
              drawLine(
                color = gridColor,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
              )
            }
            .border(2.dp, Color(0xFFB9B6FF))
        ) {
          state = rememberZoomableState(
            zoomSpec = ZoomSpec(
              minZoomFactor = 2f,
              maxZoomFactor = 3f,
            )
          )
          Box(
            Modifier
              .size(contentSize)
              .zoomable(state, clipToBounds = false)
              .background(Color(0xFF504E9A))
          )
        }
      }
    }

    rule.waitUntil {
      state.contentTransformation.isSpecified
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
      assertThat(state.contentTransformation.scaleMetadata.userZoom).isEqualTo(2f)
    }
  }

  @Test fun start_listening_to_gestures_even_if_content_is_not_ready_for_interaction() {
    var clickCount = 0
    lateinit var zoomableState: ZoomableState

    rule.setContent {
      zoomableState = rememberZoomableState(
        autoApplyTransformations = false
      )
      zoomableState.setContentLocation(ZoomableContentLocation.Unspecified)
      Box(
        Modifier
          .fillMaxSize()
          .zoomable(
            state = zoomableState,
            onClick = { clickCount++ },
          )
          .testTag("content")
      )
    }

    rule.onNodeWithTag("content").performClick()
    rule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong())
    rule.runOnIdle {
      check(!zoomableState.asReal().isReadyForInteraction)
      assertThat(clickCount).isEqualTo(1)
    }

    // Regression test for https://github.com/saket/telephoto/issues/93
    // Transformation gestures (for zooming and panning) made before the
    // content was ready was causing a crash.
    check(!zoomableState.asReal().isReadyForInteraction)
    rule.onNodeWithTag("content").performTouchInput {
      pinchToZoomInBy(visibleSize.center / 2f)
    }
  }

  @Test fun consume_gestures_immediately_if_multiple_pointer_events_are_detected() {
    lateinit var state: ZoomableState

    rule.setContent {
      Box(
        Modifier
          .fillMaxSize()
          .zoomable(rememberZoomableState().also { state = it })
          .testTag("content")
      )
    }

    rule.runOnIdle {
      assertThat(state.zoomFraction).isEqualTo(0f)
    }

    val touchSlop = ViewConfiguration.get(rule.activity).scaledTouchSlop
    rule.onNodeWithTag("content").performTouchInput {
      // I should use touchSlop here, but https://issuetracker.google.com/issues/275752829.
      val distance = Offset(x = 0f, y = 1f)
      assertThat(distance.getDistance()).isLessThan(touchSlop.toFloat())
      pinch(
        start0 = center,
        start1 = center,
        end0 = center,
        end1 = center + distance,
      )
    }
    rule.runOnIdle {
      assertThat(state.zoomFraction!!).isGreaterThan(0f)
    }
  }

  // Regression test for:
  // - https://github.com/saket/telephoto/issues/70
  // - https://github.com/saket/telephoto/issues/72
  @Test fun recycling_of_zoomable_modifier_works() {
    val pageNames = listOf("page_a", "page_b")
    val zoomFractions = mutableMapOf<String, Float?>()

    rule.setContent {
      val pagerState = rememberPagerState(
        pageCount = { pageNames.size },
      )
      HorizontalPager(
        modifier = Modifier
          .fillMaxSize()
          .testTag("pager"),
        state = pagerState,
        key = { pageNames[it] },
      ) { pageNum ->
        val zoomableState = rememberZoomableState().also {
          val pageName = pageNames[pageNum]
          zoomFractions[pageName] = it.zoomFraction
        }
        Box(
          Modifier
            .fillMaxSize()
            .zoomable(zoomableState)
            .testTag("content")
        )
      }
    }

    // Steps taken from https://github.com/saket/telephoto/issues/72#issuecomment-1980497743.
    rule.onNodeWithTag("pager").run {
      performScrollToKey("page_b")
      performScrollToKey("page_a")
    }
    rule.onNodeWithTag("content").performTouchInput {
      doubleClick()
    }

    rule.runOnIdle {
      assertThat(zoomFractions).containsOnly(
        "page_a" to 1f,
        "page_b" to 0f,
      )
    }
  }

  @Test fun double_tap_still_works_when_gestures_are_toggled() {
    lateinit var state: ZoomableState
    val enabledGestures = mutableStateOf(EnabledZoomGestures.None)

    rule.setContent {
      state = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 2f)
      )
      Box(
        Modifier
          .fillMaxSize()
          .zoomable(state, gestures = enabledGestures.value)
          .testTag("content")
      )
    }

    rule.runOnIdle {
      assertThat(state.zoomFraction!!).isEqualTo(0f)
    }

    rule.onNodeWithTag("content").performTouchInput { doubleClick() }
    rule.runOnIdle {
      assertThat(state.zoomFraction!!).isEqualTo(0f)
    }

    enabledGestures.value = EnabledZoomGestures.ZoomAndPan
    rule.onNodeWithTag("content").performTouchInput { doubleClick() }
    rule.runOnIdle {
      assertThat(state.zoomFraction!!).isEqualTo(1f)
    }

    enabledGestures.value = EnabledZoomGestures.None
    rule.onNodeWithTag("content").performTouchInput { doubleClick() }
    rule.runOnIdle {
      assertThat(state.zoomFraction!!).isEqualTo(1f)
    }
  }

  @Test fun zoomable_state_can_be_updated() {
    var key by mutableStateOf("a")
    lateinit var lastZoomableState: ZoomableState

    rule.setContent {
      val zoomableState = key(key) {
        rememberZoomableState(ZoomSpec(maxZoomFactor = 2f))
      }.also {
        lastZoomableState = it
      }

      Box(
        Modifier
          .fillMaxSize()
          .zoomable(zoomableState)
          .testTag("content")
      )
    }
    val content = rule.onNodeWithTag("content")
    val firstZoomableState = lastZoomableState

    content.performTouchInput { doubleClick() }
    rule.runOnIdle {
      assertThat(firstZoomableState.zoomFraction).isEqualTo(1f)
    }

    rule.runOnIdle { key = "b" }
    rule.waitUntil { lastZoomableState != firstZoomableState }
    rule.mainClock.advanceTimeByFrame()

    val secondZoomableState = lastZoomableState
    assertThat(secondZoomableState).isNotSameInstanceAs(firstZoomableState)

    // The new state won't retain content transformations from the previous state.
    with(secondZoomableState) {
      assertThat(zoomFraction).isNotEqualTo(firstZoomableState.zoomFraction)
      assertThat(zoomFraction).isEqualTo(0f)
      assertThat(contentTransformation.offset).isEqualTo(Offset.Zero)
    }

    // While content transformations aren't retained, the new state should still get
    // hydrated with enough information by the modifier node that it can display the content.
    with(secondZoomableState.contentTransformation) {
      assertThat(isSpecified).isTrue()
      @Suppress("DEPRECATION")
      assertThat(contentSize).isEqualTo(firstZoomableState.contentTransformation.contentSize)

      val firstContentSize = with(firstZoomableState.coordinateSystem) {
        unscaledContentBounds.sizeIn(CoordinateSpace.Viewport)
      }
      val secondContentSize = with(secondZoomableState.coordinateSystem) {
        unscaledContentBounds.sizeIn(CoordinateSpace.Viewport)
      }
      assertThat(secondContentSize).isEqualTo(firstContentSize)
    }

    // Zoom gestures should update the new state object.
    content.performTouchInput {
      doubleClick()
    }
    rule.runOnIdle {
      assertThat(secondZoomableState.zoomFraction).isEqualTo(1f)
      assertThat(secondZoomableState.contentTransformation.offset).isEqualTo(Offset(-540f, -1200f))
    }

    content.performTouchInput {
      swipeLeft(startX = centerRight.x, endX = centerLeft.x)
    }
    rule.runOnIdle {
      assertThat(secondZoomableState.contentTransformation.offset).isEqualTo(Offset(-1080f, -1200f))
    }
  }

  @Test fun disable_over_and_under_zoom() {
    val observedScales = ArrayDeque<ScaleFactor>()
    rule.setContent {
      val zoomableState = rememberZoomableState(
        ZoomSpec(
          maxZoomFactor = 2f,
          overzoomEffect = OverzoomEffect.Disabled,
        )
      )
      Box(
        Modifier
          .fillMaxSize()
          .zoomable(zoomableState)
          .testTag("content")
      )

      LaunchedEffect(Unit) {
        snapshotFlow { zoomableState.contentTransformation.scale }.collect {
          observedScales.addLast(it)
        }
      }
    }

    val content = rule.onNodeWithTag("content")
    content.performTouchInput {
      doubleClick()
    }
    rule.runOnIdle {
      assertThat(observedScales.removeAll()).contains(ScaleFactor(2f, 2f))
    }

    content.performTouchInput {
      pinchToZoomInBy(IntOffset(10, 10))
    }
    rule.runOnIdle {
      assertThat(observedScales.removeAll()).isEmpty()
    }
  }

  @Test fun pan_and_zoom_from_code(
    @TestParameter animate: Boolean,
  ) {
    lateinit var state: ZoomableState
    var startZoom by mutableStateOf(false)
    var startPan by mutableStateOf(false)

    rule.setContent {
      Box(
        Modifier
          .fillMaxSize()
          .zoomable(rememberZoomableState().also { state = it })
      )

      if (startZoom) {
        LaunchedEffect(Unit) {
          state.zoomBy(
            zoomFactor = 1.3f,
            animationSpec = if (animate) ZoomableState.DefaultZoomAnimationSpec else SnapSpec(),
          )
        }
      }
      if (startPan) {
        LaunchedEffect(Unit) {
          state.panBy(
            offset = Offset(x = 100f, y = 150f),
            animationSpec = if (animate) ZoomableState.DefaultPanAnimationSpec else SnapSpec(),
          )
        }
      }
    }

    rule.runOnIdle {
      startZoom = true
    }
    rule.runOnIdle {
      state.contentTransformation.run {
        assertThat(scale.toString()).isEqualTo(ScaleFactor(1.3f, 1.3f).toString())
        assertThat(offset.toString()).isEqualTo(Offset(-161.9f, -359.9f).toString())
      }
    }

    startPan = true
    rule.runOnIdle {
      state.contentTransformation.run {
        assertThat(scale.toString()).isEqualTo(ScaleFactor(1.3f, 1.3f).toString())
        assertThat(offset.toString()).isEqualTo(Offset(-61.9f, -209.9f).toString())
      }
    }
  }

  @Test fun invalid_zoom_requests_should_not_crash() = runTest {
    val zoomRequests = Channel<suspend (ZoomableState) -> Unit>()

    rule.setContent {
      val state = rememberZoomableState()
      Box(
        Modifier
          .fillMaxSize()
          .zoomable(state)
      )
      LaunchedEffect(Unit) {
        zoomRequests.consumeAsFlow().collect { it(state) }
      }
    }

    for (invalidFactor in listOf(-1f, 0f, Float.MAX_VALUE)) {
      rule.waitForIdle()
      zoomRequests.send { state ->
        state.zoomBy(zoomFactor = invalidFactor)
      }
    }
    for (invalidFactor in listOf(-1f, 0f, Float.MAX_VALUE)) {
      rule.waitForIdle()
      zoomRequests.send { state ->
        state.zoomTo(zoomFactor = invalidFactor)
      }
    }
  }

  @Test fun zoomTo_should_play_pan_animation_even_when_zoom_delta_is_zero(
    @TestParameter focalPoint: ZoomFocalPointParam
  ) = runTest {
    val zoomRequests = Channel<suspend (ZoomableState) -> Unit>()

    rule.setContent {
      val painter = assetPainter("cat_1920.jpg")
      val state = rememberZoomableState(ZoomSpec(maxZoomFactor = 4f)).also {
        it.setContentLocation(
          @Suppress("DEPRECATION")
          ZoomableContentLocation.scaledToFitAndCenterAligned(painter.intrinsicSize)
        )
      }
      Image(
        modifier = Modifier
          .fillMaxSize()
          .zoomable(state),
        painter = painter,
        contentDescription = null,
      )
      LaunchedEffect(Unit) {
        zoomRequests.consumeAsFlow().collect { it(state) }
      }
    }

    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_before")
    }

    zoomRequests.send { state ->
      val contentBoundsInViewport = with(state.coordinateSystem) {
        contentBounds.rectIn(CoordinateSpace.Viewport)
      }
      state.zoomTo(
        zoomFactor = state.zoomSpec.maximum.factor,
        focal = focalPoint.create(
          SpatialOffset(contentBoundsInViewport.topCenter, CoordinateSpace.Viewport)
        )
      )
    }

    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_first_zoom")
    }

    // Zoom again with the same zoom factor, but with a different
    // focal point. This time, the content should pan to the new point.
    zoomRequests.send { state ->
      val contentBoundsInViewport = with(state.coordinateSystem) {
        contentBounds.rectIn(CoordinateSpace.Viewport)
      }
      state.zoomTo(
        zoomFactor = state.zoomSpec.maximum.factor,
        focal = focalPoint.create(
          SpatialOffset(contentBoundsInViewport.bottomCenter, CoordinateSpace.Viewport)
        )
      )
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_second_zoom")
    }
  }

  @Test fun correctly_calculate_isAnimationRunning() {
    lateinit var state: ZoomableState
    val animatedZoomTriggers = Channel<Float>(capacity = 5)
    val recordedValues = ArrayDeque<Boolean>()

    rule.setContent {
      state = rememberZoomableState(
        ZoomSpec(
          maxZoomFactor = 2f,
          overzoomEffect = OverzoomEffect.NoLimits,
        )
      )
      Box(
        Modifier
          .fillMaxSize()
          .zoomable(state)
          .testTag("content")
      )

      LaunchedEffect(Unit) {
        animatedZoomTriggers.consumeAsFlow().collect {
          state.zoomTo(zoomFactor = it)
        }
      }
      LaunchedEffect(Unit) {
        snapshotFlow { state.isAnimationRunning }
          .collect {
            recordedValues.addLast(it)
          }
      }
    }

    rule.waitUntil { state.contentTransformation.isSpecified }
    assertThat(recordedValues.removeAll()).containsOnly(false)

    rule.onNodeWithTag("content").performTouchInput { doubleClick() }
    rule.waitUntil { state.contentTransformation.scaleMetadata.userZoom == 2f }
    assertThat(recordedValues.removeAll()).containsOnly(true, false)

    // Pans made by the user should not cause any animation.
    rule.onNodeWithTag("content").performTouchInput {
      swipeLeft(startX = center.x, endX = center.x - 2f)
    }
    rule.runOnIdle {
      assertThat(recordedValues.removeAll()).isEmpty()
    }

    animatedZoomTriggers.trySend(0.5f)
    rule.waitUntil {
      state.contentTransformation.scaleMetadata.userZoom == 1f
    }
    rule.runOnIdle {
      assertThat(recordedValues.removeAll()).containsOnly(true, false)
    }
  }

  @Test fun prevent_precision_errors_during_settle_animation() {
    lateinit var state: ZoomableState
    var isZoomComplete = false

    rule.setContent {
      state = rememberZoomableState(
        ZoomSpec(
          maxZoomFactor = 1f,
          overzoomEffect = OverzoomEffect.NoLimits,
        )
      )
      Box(
        Modifier
          .size(200.dp, 300.dp)
          .zoomable(state)
          .testTag("content")
      )

      if (state.contentTransformation.isSpecified) {
        LaunchedEffect(Unit) {
          state.zoomTo(0.4495843f)
          isZoomComplete = true
        }
      }
    }

    rule.waitUntil { isZoomComplete }
    rule.runOnIdle {
      assertThat(state.contentTransformation.scaleMetadata.userZoom).isEqualTo(1f)
    }
  }

  @Test fun nullability_of_click_listeners_can_be_changed() {
    lateinit var state: ZoomableState
    var onClickCount = 0
    var onLongClickCount = 0
    var onDoubleClickCount = 0

    var onClick: ((Offset) -> Unit)? by mutableStateOf({ onClickCount++ })
    var onLongClick: ((Offset) -> Unit)? by mutableStateOf({ onLongClickCount++ })
    var onDoubleClick: DoubleClickToZoomListener? by mutableStateOf(
      DoubleClickToZoomListener { _, _ -> onDoubleClickCount++ }
    )

    rule.setContent {
      state = rememberZoomableState()
      Box(
        Modifier
          .size(200.dp, 300.dp)
          .testTag("content")
          .zoomable(
            state = state,
            onClick = onClick,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
          )
      )
    }

    fun sendClicks() {
      rule.onNodeWithTag("content").performClick()
      rule.mainClock.advanceTimeBy(ViewConfiguration.getDoubleTapTimeout().toLong())

      rule.onNodeWithTag("content").performTouchInput { longClick() }
      rule.onNodeWithTag("content").performTouchInput { doubleClick() }
    }

    sendClicks()
    rule.runOnIdle {
      assertThat(onClickCount).isEqualTo(1)
      assertThat(onLongClickCount).isEqualTo(1)
      assertThat(onDoubleClickCount).isEqualTo(1)
    }

    onClick = null
    sendClicks()
    rule.runOnIdle {
      assertThat(onClickCount).isEqualTo(1)
      assertThat(onLongClickCount).isEqualTo(2)
      assertThat(onDoubleClickCount).isEqualTo(2)
    }

    onLongClick = null
    sendClicks()
    rule.runOnIdle {
      assertThat(onClickCount).isEqualTo(1)
      assertThat(onLongClickCount).isEqualTo(2)
      assertThat(onDoubleClickCount).isEqualTo(3)
    }

    onDoubleClick = null
    sendClicks()
    rule.runOnIdle {
      assertThat(onClickCount).isEqualTo(1)
      assertThat(onLongClickCount).isEqualTo(2)
      assertThat(onDoubleClickCount).isEqualTo(3)
    }

    onClick = { onClickCount++ }
    onLongClick = { onLongClickCount++ }
    onDoubleClick = DoubleClickToZoomListener { _, _ -> onDoubleClickCount++ }

    sendClicks()
    rule.runOnIdle {
      assertThat(onClickCount).isEqualTo(2)
      assertThat(onLongClickCount).isEqualTo(3)
      assertThat(onDoubleClickCount).isEqualTo(4)
    }
  }

  @Test fun clickable_modifier_can_be_used_when_all_click_listeners_are_null() {
    var onClickCalled = false

    rule.setContent {
      val state = rememberZoomableState()
      Box(
        Modifier
          .size(200.dp, 300.dp)
          .testTag("content")
          .zoomable(
            state = state,
            onClick = null,
            onLongClick = null,
            onDoubleClick = null,
            gestures = EnabledZoomGestures.NoQuickZoom,
          )
          .clickable { onClickCalled = true }
      )
    }

    rule.onNodeWithTag("content").performClick()
    rule.runOnIdle {
      assertThat(onClickCalled).isTrue()
    }
  }

  @Test fun all_spatial_values_are_unspecified_when_content_is_not_measured_yet() {
    lateinit var state: ZoomableState
    rule.setContent {
      state = rememberZoomableState()
    }

    rule.waitForIdle()
    with(state.coordinateSystem) {
      assertThat(viewportSize).isEqualTo(Size.Zero)

      assertThat(
        SpatialOffset(Offset.Zero, CoordinateSpace.Viewport).offsetIn(CoordinateSpace.ZoomableContent)
      ).isEqualTo(Offset.Unspecified)
      assertThat(
        SpatialOffset(Offset.Zero, CoordinateSpace.ZoomableContent).offsetIn(CoordinateSpace.Viewport)
      ).isEqualTo(Offset.Unspecified)

      assertThat(contentBounds).isEqualTo(SpatialRect.Unspecified)
      assertThat(contentBounds.rectIn(CoordinateSpace.ZoomableContent)).isEqualTo(Rect.Zero)

      assertThat(unscaledContentBounds).isEqualTo(SpatialRect.Unspecified)
      assertThat(unscaledContentBounds.rectIn(CoordinateSpace.ZoomableContent)).isEqualTo(Rect.Zero)
    }
  }

  @Test fun resolve_unresolved_spatial_values() {
    lateinit var state: ZoomableState
    rule.setContent {
      state = rememberZoomableState()

      Box(
        Modifier
          .size(200.dp, 300.dp)
          .testTag("content")
          .zoomable(state)
      )
    }

    rule.waitUntil {
      rule.onNodeWithTag("content").isDisplayed()
    }

    with(state.coordinateSystem) {
      assertThat(
        SpatialOffset.Unspecified.offsetIn(CoordinateSpace.Viewport)
      ).isEqualTo(Offset.Unspecified)

      assertThat(
        SpatialOffset(Offset.Unspecified, CoordinateSpace.Viewport).offsetIn(CoordinateSpace.ZoomableContent)
      ).isEqualTo(Offset.Unspecified)

      assertThat(
        SpatialRect.Unspecified.rectIn(CoordinateSpace.ZoomableContent)
      ).isEqualTo(Rect.Zero)
    }
  }

  @Test fun haptic_feedback() {
    lateinit var state: ZoomableState
    val hapticFeedback = RecordingHapticFeedback()

    rule.setContent {
      CompositionLocalProvider(LocalHapticFeedback provides hapticFeedback) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
          state = rememberZoomableState()
          Box(
            Modifier
              .size(200.dp)
              .zoomable(state, clipToBounds = false)
              .background(Color.Green)
              .testTag("content")
          )
        }
      }
    }

    rule.onNodeWithTag("content").performTouchInput {
      doubleClick()
    }
    rule.waitUntil { state.zoomFraction!! == 1f }

    rule.onNodeWithTag("content").performTouchInput {
      val noUpScope = object : TouchInjectionScope by this {
        override fun up(pointerId: Int) = Unit
      }
      noUpScope.pinchToZoomInBy(IntOffset(2, 2))
    }
    rule.runOnIdle {
      assertThat(hapticFeedback.performedFeedbacks).isEmpty()
    }

    rule.onNodeWithTag("content").performTouchInput {
      this.cancel()
    }
    rule.waitUntil { state.contentTransformation.scaleMetadata.userZoom == state.zoomSpec.maximum.factor }
    rule.runOnIdle {
      assertThat(hapticFeedback.performedFeedbacks.removeAll()).containsExactly(HapticFeedbackType.Reject)
    }

    rule.onNodeWithTag("content").performTouchInput {
      pinchToZoomInBy(IntOffset(-10, -10))
    }
    rule.runOnIdle {
      assertThat(hapticFeedback.performedFeedbacks.removeAll()).containsExactly(HapticFeedbackType.Reject)
    }
  }

  @Test fun interaction_source_emits_press_and_release() = runTest {
    val interactionSource = MutableInteractionSource()
    val interactions = ArrayDeque<PressInteraction>()

    rule.setContent {
      LaunchedEffect(Unit) {
        interactionSource.interactions.collect {
          check(it is PressInteraction)
          interactions.addLast(it)
        }
      }

      Box(
        Modifier
          .size(200.dp, 300.dp)
          .testTag("content")
          .zoomable(
            state = rememberZoomableState(),
            interactionSource = interactionSource,
          )
      )
    }

    fun List<PressInteraction>.mapToNames(): List<String> = map { it::class.simpleName!! }

    // Single tap.
    rule.onNodeWithTag("content").performClick()
    rule.mainClock.advanceTimeBy(ViewConfiguration.getDoubleTapTimeout().toLong())
    rule.runOnIdle {
      assertThat(interactions.removeAll().mapToNames()).containsExactly("Press", "Release")
    }

    // Long press.
    rule.onNodeWithTag("content").performTouchInput {
      longClick(center)
    }
    rule.runOnIdle {
      assertThat(interactions.removeAll().mapToNames()).containsExactly("Press", "Release")
    }

    // Double tap to zoom in.
    rule.onNodeWithTag("content").performTouchInput {
      doubleClick(center)
    }
    rule.runOnIdle {
      assertThat(interactions.removeAll().mapToNames()).containsExactly("Press", "Release", "Press", "Release")
    }

    // Quick zoom out (double-tap and drag up).
    rule.onNodeWithTag("content").performTouchInput {
      click(center)
      advanceEventTime(eventPeriodMillis + viewConfiguration.doubleTapMinTimeMillis)
      down(center)
      // Don't move yet.
    }
    rule.runOnIdle {
      // First tap released, second tap still pressed (finger still down, hasn't moved yet).
      assertThat(interactions.removeAll().mapToNames()).containsExactly("Press", "Release", "Press")
    }
    rule.onNodeWithTag("content").performTouchInput {
      // Drag up to zoom out.
      moveTo(Offset(center.x, center.y - (height / 2f)))
      up(pointerId = 0)
    }
    rule.runOnIdle {
      // The second press becomes a drag, so it's cancelled.
      assertThat(interactions.removeAll().mapToNames()).containsExactly("Cancel")
    }

    // Pinch to zoom in.
    rule.onNodeWithTag("content").performTouchInput {
      down(0, center + Offset(-10f, 0f))
      down(1, center + Offset(10f, 0f))
    }
    rule.runOnIdle {
      // Both fingers down, still pressed.
      assertThat(interactions.removeAll().mapToNames()).containsExactly("Press")
    }
    rule.onNodeWithTag("content").performTouchInput {
      // Move fingers apart to zoom in.
      moveBy(0, Offset(-30f, 0f))
      moveBy(1, Offset(30f, 0f))
      up(0)
      up(1)
    }
    rule.runOnIdle {
      // Cancelled because position change is consumed by the transform gesture.
      assertThat(interactions.removeAll().mapToNames()).containsExactly("Cancel")
    }

    // Pan.
    rule.onNodeWithTag("content").performTouchInput {
      down(center)
    }
    rule.runOnIdle {
      assertThat(interactions.removeAll().mapToNames()).containsExactly("Press")
    }
    rule.onNodeWithTag("content").performTouchInput {
      moveTo(centerLeft)
    }
    rule.runOnIdle {
      // Cancelled because position change is consumed by the pan gesture.
      assertThat(interactions.removeAll().mapToNames()).containsExactly("Cancel")
    }
    rule.onNodeWithTag("content").performTouchInput {
      up(0)
    }
    rule.runOnIdle {
      // No new interactions after lifting - Cancel already emitted.
      assertThat(interactions.removeAll().mapToNames()).isEmpty()
    }
  }

  @Test fun interaction_source_works_with_click_listeners() {
    val interactionSource = MutableInteractionSource()
    val isPressedHistory = ArrayDeque<Boolean>()
    var clickCount = 0
    var longClickCount = 0
    var doubleClickCount = 0

    rule.setContent {
      val isPressed by interactionSource.collectIsPressedAsState()
      LaunchedEffect(isPressed) {
        isPressedHistory.addLast(isPressed)
      }

      Box(
        Modifier
          .size(200.dp, 300.dp)
          .testTag("content")
          .zoomable(
            state = rememberZoomableState(),
            interactionSource = interactionSource,
            onClick = { clickCount++ },
            onLongClick = { longClickCount++ },
            onDoubleClick = DoubleClickToZoomListener { _, _ -> doubleClickCount++ },
          )
      )
    }

    rule.runOnIdle {
      isPressedHistory.clear() // Clear initial false.
    }

    // Single tap should emit press/release AND trigger onClick.
    rule.onNodeWithTag("content").performClick()
    rule.mainClock.advanceTimeBy(ViewConfiguration.getDoubleTapTimeout().toLong())
    rule.runOnIdle {
      assertThat(isPressedHistory.removeAll()).containsExactly(true, false)
      assertThat(clickCount).isEqualTo(1)
    }

    // Long press should emit press/release AND trigger onLongClick.
    rule.onNodeWithTag("content").performTouchInput {
      longClick(center)
    }
    rule.runOnIdle {
      assertThat(isPressedHistory.removeAll()).containsExactly(true, false)
      assertThat(longClickCount).isEqualTo(1)
    }

    // Double tap should emit press/release for both taps AND trigger onDoubleClick.
    rule.onNodeWithTag("content").performTouchInput {
      doubleClick(center)
    }
    rule.runOnIdle {
      assertThat(isPressedHistory.removeAll()).containsExactly(true, false, true, false)
      assertThat(doubleClickCount).isEqualTo(1)
    }
  }

  @Suppress("unused")
  enum class ZoomFocalPointParam(val create: (SpatialOffset) -> ZoomFocalPoint) {
    ZoomAroundCentroid(create = { centroid -> ZoomFocalPoint.zoomAround(centroid) }),
    MoveToViewportCenter(create = { viewportCenter -> ZoomFocalPoint.moveToViewportCenter(viewportCenter) }),
  }
}

private fun ZoomableState.asReal(): RealZoomableState {
  return this as RealZoomableState  // Safe because ZoomableState is a sealed type.
}

private fun <T> ArrayDeque<T>.removeAll(): List<T> {
  val source = this
  val destination = ArrayList(source)
  source.clear()
  return destination
}

internal fun TouchInjectionScope.pinchToZoomInBy(by: IntOffset) {
  pinch(
    start0 = center,
    start1 = center,
    end0 = center - by.toOffset(),
    end1 = center + by.toOffset(),
  )
}
