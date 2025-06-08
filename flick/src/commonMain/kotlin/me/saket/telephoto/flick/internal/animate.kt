package me.saket.telephoto.flick.internal

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Like [animate] but also provides the animation's duration.
 * Useful when a non-time-based animation spec is used.
 * */
internal suspend fun animateWithDuration(
  initialValue: Offset,
  targetValue: Offset,
  initialVelocity: Velocity,
  animationSpec: AnimationSpec<Offset>,
  onStart: (duration: Duration) -> Unit,
  block: (value: Offset) -> Unit,
) {
  val anim = TargetBasedAnimation(
    initialValue = initialValue,
    targetValue = targetValue,
    typeConverter = Offset.VectorConverter,
    animationSpec = animationSpec,
    initialVelocity = Offset(initialVelocity.x, initialVelocity.y),
  )
  onStart(anim.durationNanos.nanoseconds)
  animate(
    initialValue = initialValue,
    targetValue = targetValue,
    initialVelocity = Offset(initialVelocity.x, initialVelocity.y),
    animationSpec = animationSpec,
    block = { value, _ -> block(value) },
    typeConverter = Offset.VectorConverter,
  )
}
