package me.saket.telephoto.flick

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dismissed
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dismissing
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dragging
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Idle
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Resetting
import me.saket.telephoto.flick.FlickToDismissTest.DragStartLocationParam.DragStartedOnLeftSide
import me.saket.telephoto.flick.FlickToDismissTest.DragStartLocationParam.DragStartedOnRightSide
import me.saket.telephoto.flick.FlickToDismissTest.SwipeDirectionParam.DownwardSwipe
import me.saket.telephoto.flick.FlickToDismissTest.SwipeDirectionParam.UpwardSwipe
import me.saket.telephoto.flick.RealFlickToDismissState.Companion.FlingSlopMultiplier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@RunWith(TestParameterInjector::class)
@OptIn(ExperimentalTelephotoApi::class)
class FlickToDismissTest {

  @get:Rule val paparazzi = Paparazzi(
    deviceConfig = deviceConfig,
    renderingMode = RenderingMode.SHRINK,
  )
  private val deviceConfig get() = DeviceConfig.PIXEL_5

  @Test fun idle() {
    val state = RealFlickToDismissState()
    assertThat(state.gestureState).isEqualTo(Idle)

    paparazzi.snapshot {
      Surface {
        FlickToDismiss(state) {
          Box(
            Modifier
              .fillMaxWidth()
              .padding(vertical = 24.dp)
              .background(MaterialTheme.colorScheme.tertiary)
              .height(200.dp)
          )
        }
      }
    }
  }

  // todo: use paparazzi.gif
  @Test fun `apply rotation during drag`(
    @TestParameter dragStartedAt: DragStartLocationParam
  ) = runBlocking {
    val state = RealFlickToDismissState().apply {
      contentSize = IntSize(width = deviceConfig.screenWidth, height = 0)
      handleOnDragStarted(
        when (dragStartedAt) {
          DragStartedOnLeftSide -> Offset(x = deviceConfig.screenWidth * 0.4f, y = 0f)
          DragStartedOnRightSide -> Offset(x = deviceConfig.screenWidth * 0.8f, y = 0f)
        }
      )
      draggableState.drag {
        dragBy(Offset(0f, y = dpToPx(-100f)))
      }
    }

    paparazzi.snapshot {
      Surface {
        FlickToDismiss(state) {
          Box(
            Modifier
              .fillMaxWidth()
              .padding(vertical = 24.dp)
              .background(MaterialTheme.colorScheme.tertiary)
              .height(200.dp)
          )
        }
      }
    }
  }

  @Test fun `account for rotation when calculating dismiss offset`(
    @TestParameter swipeDirection: SwipeDirectionParam
  ) = runBlocking {
    val state = RealFlickToDismissState()
    state.contentSize = IntSize(width = deviceConfig.screenWidth, height = dpToPx(300))
    state.draggableState.drag {
      dragBy(
        Offset(
          x = 0f, y = when (swipeDirection) {
            UpwardSwipe -> -1f
            DownwardSwipe -> 1f
          }
        )
      )
    }

    val scope = CoroutineScope(Dispatchers.IO)
    scope.launchMolecule(mode = RecompositionMode.Immediate) {
      LaunchedEffect(Unit) {
        state.animateDismissal(velocity = Velocity.Zero)
      }
    }
    while (true) {
      if (state.gestureState is Dismissed) {
        scope.cancel()
        break
      }
    }

    paparazzi.snapshot {
      Surface {
        FlickToDismiss(
          state = state,
          modifier = Modifier
            .padding(vertical = 16.dp)
            .border(Dp.Hairline, Color.Black)
        ) {
          Box(
            Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.tertiary)
              .height(LocalDensity.current.run { state.contentSize.height.toDp() })
          )
        }
      }
    }
  }

  @Test fun `content can be dismissed after it has reached its dismiss threshold`() = runTest {
    val state = RealFlickToDismissState()
    state.contentSize = IntSize(width = deviceConfig.screenWidth, height = dpToPx(300))

    state.draggableState.drag {
      dragBy(Offset(0f, 1f))  // This will move the gesture state from Idle to Dragging.
    }

    // When a fling is registered, the content can be dismissed if the velocity
    // is sufficient even if the content wasn't dragged past its threshold distance.
    val flingVelocity = (state.contentSize.height * state.dismissThresholdRatio + 1f) * FlingSlopMultiplier
    assertThat(state.willDismissOnRelease(velocity = flingVelocity)).isTrue()

    val dragNeededToDismiss = state.contentSize.height * state.dismissThresholdRatio + 1f
    state.draggableState.drag {
      dragBy(Offset(0f, dragNeededToDismiss - 5f))
    }
    assertThat(state.willDismissOnRelease(velocity = 0f)).isFalse()

    state.draggableState.drag {
      dragBy(Offset(0f, 5f))
    }
    assertThat(state.willDismissOnRelease(velocity = 0f)).isTrue()
  }

  // todo: use paparazzi.gif
  @Test fun `play reset animation`() = runTest {
    val state = RealFlickToDismissState()
    state.contentSize = IntSize(width = deviceConfig.screenWidth, height = dpToPx(300))

    state.draggableState.drag {
      dragBy(Offset(dpToPx(70f), dpToPx(50f)))
    }
    assertThat(state.gestureState).isEqualTo(Dragging(willDismissOnRelease = false))

    val gestureStates = backgroundScope.launchMolecule(mode = RecompositionMode.Immediate) {
      LaunchedEffect(Unit) {
        state.animateReset()
      }
      state.gestureState
    }
    gestureStates.test {
      skipItems(1)  // Dragging state.
      assertThat(awaitItem()).isEqualTo(Resetting)
      assertThat(awaitItem()).isEqualTo(Idle)
      assertThat(state.offset).isEqualTo(Offset.Zero)
    }
  }

  // todo: use paparazzi.gif
  @Test fun `play dismiss animation`() = runTest {
    val state = RealFlickToDismissState()
    state.contentSize = IntSize(width = deviceConfig.screenWidth, height = dpToPx(300))

    state.draggableState.drag {
      dragBy(Offset(x = dpToPx(120f), y = dpToPx(200f)))
    }
    assertThat(state.gestureState).isEqualTo(Dragging(willDismissOnRelease = true))

    val gestureStates = backgroundScope.launchMolecule(mode = RecompositionMode.Immediate) {
      LaunchedEffect(Unit) {
        state.animateDismissal(Velocity.Zero)
      }
      state.gestureState
    }
    gestureStates.test {
      skipItems(1)  // Dragging state.
      assertThat(awaitItem()).isInstanceOf<Dismissing>()
      assertThat(awaitItem()).isEqualTo(Dismissed)
      state.offset.also {
        assertThat(it.x).isEqualTo(dpToPx(120f))
        assertThat(it.y).isGreaterThan(dpToPx(300f))
      }
    }
  }

  @Test fun `calculate offset fraction correctly`() = runTest {
    val state = RealFlickToDismissState()
    assertThat(state.offsetFraction).isEqualTo(0f)

    state.contentSize = IntSize(width = deviceConfig.screenWidth, height = dpToPx(300))
    assertThat(state.offsetFraction).isEqualTo(0f)

    state.draggableState.drag {
      dragBy(Offset(10f, dpToPx(50f)))
    }
    assertThat(state.offsetFraction).isCloseTo(0.16f, delta = 0.01f)

    // Offset fraction should be positive even if the offset is negative.
    state.draggableState.drag {
      dragBy(Offset(-15f, dpToPx(-200f)))
    }
    assertThat(state.offsetFraction).isEqualTo(0.5f)

    // Offset fraction should remain within its bounds even when the content is dismissed beyond its height.
    state.draggableState.drag {
      dragBy(Offset(x = -state.contentSize.width * 2f, y = -state.contentSize.height * 2f))
    }
    assertThat(state.offsetFraction).isEqualTo(1f)
  }

  @Test fun `play haptic feedback`() {
    val state = RealFlickToDismissState(
      dismissThresholdRatio = 0.2f,
      rotateOnDrag = false,
    )

    val recordingHapticFeedback = object : HapticFeedback {
      var count by mutableIntStateOf(0)

      override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        count++
      }
    }

    paparazzi.gif(fps = 60, end = 3.seconds) {
      Box(
        Modifier
          .fillMaxWidth()
          .height(1920.dp)
          .background(MaterialTheme.colorScheme.background)
      ) {
        CompositionLocalProvider(LocalHapticFeedback provides recordingHapticFeedback) {
          FlickToDismiss(
            modifier = Modifier.fillMaxSize(),
            state = state,
          ) {
            Box(
              Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondary)
                .padding(vertical = 100.dp),
            )
          }
        }

        BasicText(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(24.dp),
          text = "Haptic feedback count = ${recordingHapticFeedback.count}",
          style = TextStyle(
            fontSize = 24.sp,
            color = Color.Black,
          ),
        )
      }

      // todo: open source TouchRobot from Cash App for drawing these gestures in a much easier way.
      if (state.contentSize != IntSize.Zero) {
        LaunchedEffect(Unit) {
          state.handleOnDragStarted(Offset(x = 20f, y = state.contentSize.height / 2f))

          // Drag until the threshold is crossed.
          val thresholdDistance = (state.dismissThresholdRatio * state.contentSize.height).toInt()
          val pxPerMove = 10f

          state.draggableState.drag(MutatePriority.UserInput) {
            repeat((thresholdDistance + dpToPx(24)) / pxPerMove.toInt()) {
              dragBy(Offset(0f, -pxPerMove))
              delay(1)
            }
          }

          delay(250)

          // Reverse drag to move back under the threshold.
          state.draggableState.drag(MutatePriority.UserInput) {
            repeat(thresholdDistance / pxPerMove.toInt()) {
              dragBy(Offset(0f, pxPerMove))
              delay(1)
            }
          }

          delay(250)

          // This time, trigger a dismiss directly, without crossing the threshold.
          // Only one haptic feedback should be played in response to this.
          state.animateDismissal(velocity = Velocity(-1000f, -1000f))
          assertThat(recordingHapticFeedback.count).isEqualTo(3)
        }
      }
    }
  }

  enum class DragStartLocationParam {
    DragStartedOnLeftSide,
    DragStartedOnRightSide,
  }

  enum class SwipeDirectionParam {
    UpwardSwipe,
    DownwardSwipe,
  }

  private fun dpToPx(value: Int): Int {
    return dpToPx(value.toFloat()).roundToInt()
  }

  private fun dpToPx(value: Float): Float {
    val density = paparazzi.resources.displayMetrics.density
    return density * value
  }
}
