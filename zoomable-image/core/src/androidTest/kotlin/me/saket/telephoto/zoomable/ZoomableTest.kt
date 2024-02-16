package me.saket.telephoto.zoomable

import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import com.dropbox.dropshots.Dropshots
import leakcanary.LeakAssertions
import me.saket.telephoto.util.prepareForScreenshotTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// TODO: move these tests to :zoomable
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
}
