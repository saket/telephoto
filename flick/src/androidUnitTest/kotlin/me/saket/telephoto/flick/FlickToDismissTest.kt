package me.saket.telephoto.flick

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dismissed
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dismissing
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dragging
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Idle
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Resetting
import me.saket.telephoto.flick.FlickToDismissTest.DragStartLocationParam.*
import me.saket.telephoto.flick.FlickToDismissTest.SwipeDirectionParam.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(TestParameterInjector::class)
class FlickToDismissTest {

  @get:Rule val paparazzi = Paparazzi(
    deviceConfig = deviceConfig,
    renderingMode = RenderingMode.SHRINK,
  )
  private val deviceConfig get() = DeviceConfig.PIXEL_5
  private val context: Context get() = paparazzi.context

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
        dragBy(context.dp(-100f))
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
    state.contentSize = IntSize(width = deviceConfig.screenWidth, height = context.dp(300))
    state.draggableState.drag {
      dragBy(
        when (swipeDirection) {
          UpwardSwipe -> -1f
          DownwardSwipe -> 1f
        }
      )
    }

    val scope = CoroutineScope(Dispatchers.IO)
    scope.launchMolecule(mode = RecompositionMode.Immediate) {
      LaunchedEffect(Unit) {
        state.animateDismissal(velocity = 0f)
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
    state.contentSize = IntSize(width = deviceConfig.screenWidth, height = context.dp(300))

    state.draggableState.drag {
      dragBy(1f)  // This will move the gesture state from Idle to Dragging.
    }

    // When a fling is registered, the content can be dismissed if the velocity
    // is sufficient even if the content wasn't dragged past its threshold distance.
    val flingVelocity = state.contentSize.height * state.dismissThresholdRatio + 1f
    assertThat(state.willDismissOnRelease(velocity = flingVelocity)).isTrue()

    val dragNeededToDismiss = state.contentSize.height * state.dismissThresholdRatio + 1f
    state.draggableState.drag {
      dragBy(dragNeededToDismiss - 5f)
    }
    assertThat(state.willDismissOnRelease(velocity = 0f)).isFalse()

    state.draggableState.drag {
      dragBy(5f)
    }
    assertThat(state.willDismissOnRelease(velocity = 0f)).isTrue()
  }

  @Test fun `play reset animation`() = runTest {
    val state = RealFlickToDismissState()
    state.contentSize = IntSize(width = deviceConfig.screenWidth, height = context.dp(300))

    state.draggableState.drag {
      dragBy(context.dp(50f))
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
      assertThat(state.offset).isEqualTo(0f)
    }
  }

  @Test fun `play dismiss animation`() = runTest {
    val state = RealFlickToDismissState()
    state.contentSize = IntSize(width = deviceConfig.screenWidth, height = context.dp(300))

    state.draggableState.drag {
      dragBy(context.dp(200f))
    }
    assertThat(state.gestureState).isEqualTo(Dragging(willDismissOnRelease = true))

    val gestureStates = backgroundScope.launchMolecule(mode = RecompositionMode.Immediate) {
      LaunchedEffect(Unit) {
        state.animateDismissal(velocity = 0f)
      }
      state.gestureState
    }
    gestureStates.test {
      skipItems(1)  // Dragging state.
      assertThat(awaitItem()).isInstanceOf<Dismissing>()
      assertThat(awaitItem()).isEqualTo(Dismissed)
      assertThat(state.offset).isGreaterThan(context.dp(300f))
    }
  }

  @Test fun `calculate offset fraction correctly`() = runTest {
    val state = RealFlickToDismissState()
    assertThat(state.offsetFraction).isEqualTo(0f)

    state.contentSize = IntSize(width = deviceConfig.screenWidth, height = context.dp(300))
    assertThat(state.offsetFraction).isEqualTo(0f)

    state.draggableState.drag {
      dragBy(context.dp(50f))
    }
    assertThat(state.offsetFraction).isCloseTo(0.16f, delta = 0.01f)

    // Offset fraction should be positive even if the offset is negative.
    state.draggableState.drag {
      dragBy(context.dp(-200f))
    }
    assertThat(state.offsetFraction).isEqualTo(0.5f)

    // Offset fraction should remain within its bounds even when the content is dismissed beyond its height.
    state.draggableState.drag {
      dragBy(-state.contentSize.height * 2f)
    }
    assertThat(state.offsetFraction).isEqualTo(1f)
  }

  enum class DragStartLocationParam {
    DragStartedOnLeftSide,
    DragStartedOnRightSide,
  }

  enum class SwipeDirectionParam {
    UpwardSwipe,
    DownwardSwipe,
  }

  private fun Context.dp(value: Int): Int {
    return dp(value.toFloat()).roundToInt()
  }

  private fun Context.dp(value: Float): Float {
    val density = resources.displayMetrics.density
    return density * value
  }
}
