package me.saket.telephoto.zoomable

import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dropbox.dropshots.Dropshots
import com.google.common.truth.Truth.assertThat
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
          .padding(16.dp)
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
}
