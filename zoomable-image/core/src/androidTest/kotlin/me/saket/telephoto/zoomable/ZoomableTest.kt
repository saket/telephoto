package me.saket.telephoto.zoomable

import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.SnapSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import com.dropbox.dropshots.Dropshots
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import leakcanary.LeakAssertions
import me.saket.telephoto.util.prepareForScreenshotTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// TODO: move these tests to :zoomable
@RunWith(TestParameterInjector::class)
class ZoomableTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val dropshots = Dropshots(
    filenameFunc = { "zoomable_$it" },
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

  @Test fun start_listening_to_gestures_even_if_content_is_not_ready_for_interaction() {
    var clickCount = 0

    rule.setContent {
      val zoomableState = rememberZoomableState(
        autoApplyTransformations = false,
      )
      LaunchedEffect(zoomableState) {
        zoomableState.setContentLocation(ZoomableContentLocation.Unspecified)
      }
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
      assertThat(clickCount).isEqualTo(1)
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
      val distance = Offset(x = 0f, y = 1f) // I should use touchSlop here, but https://issuetracker.google.com/issues/275752829.
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
  @OptIn(ExperimentalFoundationApi::class)
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
}
