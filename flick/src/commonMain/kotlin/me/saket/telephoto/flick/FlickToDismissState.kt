package me.saket.telephoto.flick

import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun rememberFlickToDismissState(): FlickToDismissState {
  return remember { RealFlickToDismissState() }
}

sealed interface FlickToDismissState {
  val offset: Float
  val rotationZ: Float
  var gestureState: GestureState

  /**
   * Distance dragged as a fraction of the content's height.
   *
   * @return A value between 0 and 1, where 0 indicates that the content is fully dismissed
   * and 1 indicates that the content is fully settled in its default position.
   */
  @get:FloatRange(from = -1.0, to = 1.0)
  val offsetFraction: Float

  sealed interface GestureState {
    object Idle : GestureState

    data class Dragging(
      val willDismissOnRelease: Boolean
    ) : GestureState

    object Resetting : GestureState
    object Dismissing : GestureState
    object Dismissed : GestureState
  }
}
