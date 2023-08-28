package me.saket.telephoto.flick

import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlin.time.Duration

/**
 * Create a [FlickToDismissState] that can be used with [FlickToDismiss()][FlickToDismiss].
 *
 * @param dismissThresholdRatio Minimum distance the user's finger should move as a fraction
 * of the content's height after which it can be dismissed.
 *
 * @param rotateOnDrag When enabled, a subtle rotation is applied to the content while its
 * being dragged.
 */
@Composable
fun rememberFlickToDismissState(
  dismissThresholdRatio: Float = 0.3f,
  rotateOnDrag: Boolean = true,
): FlickToDismissState {
  return remember(dismissThresholdRatio, rotateOnDrag) {
    RealFlickToDismissState(
      dismissThresholdRatio = dismissThresholdRatio,
      rotateOnDrag = rotateOnDrag
    )
  }
}

@Stable
sealed interface FlickToDismissState {
  val offset: Float
  val rotationZ: Float
  var gestureState: GestureState

  /**
   * Distance dragged as a fraction of the content's height.
   *
   * @return A value between 0 and 1, where 0 indicates that the content is past its dismiss
   * threshold and 1 indicates that the content is fully settled in its default position.
   */
  @get:FloatRange(from = 0.0, to = 1.0)
  val offsetFraction: Float

  sealed interface GestureState {
    object Idle : GestureState

    data class Dragging(
      val willDismissOnRelease: Boolean
    ) : GestureState

    object Resetting : GestureState

    data class Dismissing(
      /**
       * Determines how long the content animates before it is fully dismissed.
       * This can be used for scheduling an exit of your screen.
       *
       * ```
       * val gestureState = flickState.gestureState
       *
       * if (gestureState is Dismissing) {
       *   LaunchedEffect(Unit) {
       *     delay(gestureState.animationDuration / 2f)
       *     navigator.goBack()
       *   }
       * }
       * ```
       *
       * You could also wait for the state to change to [Dismissed] as an exit signal,
       * but that might be too late as most navigation frameworks have a delay from when
       * an exit navigation is issued to when the screen actually hides from the UI.
       */
      val animationDuration: Duration
    ) : GestureState

    object Dismissed : GestureState
  }
}
