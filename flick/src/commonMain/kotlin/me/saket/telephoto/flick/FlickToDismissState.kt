package me.saket.telephoto.flick

import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import dev.drewhamilton.poko.Poko
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
  val gestureState: GestureState

  /**
   * Distance dragged as a fraction of the content's height.
   *
   * @return A value between 0 and 1, where 0 indicates that the content is fully settled in its
   * default position and 1 indicates that the content is past its dismiss threshold
   */
  @get:FloatRange(from = 0.0, to = 1.0)
  val offsetFraction: Float

  @Immutable
  sealed interface GestureState {
    /**
     * Content is resting at its default position with no ongoing drag gesture.
     */
    object Idle : GestureState

    @Poko
    class Dragging(
      /**
       * Whether the drag distance is sufficient to dismiss the content once it's released.
       *
       * The dismiss threshold is controlled by [dismissThresholdRatio][rememberFlickToDismissState].
       *
       * Keep in mind that the content can also be dismissed if the release velocity is
       * sufficient enough regardless of whether [willDismissOnRelease] is true.
       */
      val willDismissOnRelease: Boolean
    ) : GestureState

    /**
     * Content is settling back to its default position after it was released because the drag
     * distance wasn't sufficient to dismiss the content.
     */
    object Resetting : GestureState

    @Poko
    class Dismissing(
      /**
       * Determines how long the content animates before it is fully dismissed.
       * This can be used for scheduling an exit of your screen.
       *
       * ```
       * val gestureState = flickState.gestureState
       *
       * if (gestureState is Dismissing) {
       *   LaunchedEffect(Unit) {
       *     delay(gestureState.animationDuration / 2)
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

    /**
     * Content was dismissed. At this point, [FlickToDismiss] is no longer usable
     * and must be removed from composition.
     */
    object Dismissed : GestureState
  }
}
