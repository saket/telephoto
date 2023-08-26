package me.saket.telephoto.zoomable

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.dropbox.dropshots.Dropshots
import me.saket.telephoto.util.prepareForScreenshotTest
import me.saket.telephoto.util.screenshotForMinSdk23
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
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23())
    }
  }
}
