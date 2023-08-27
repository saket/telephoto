package me.saket.telephoto.flick.internal

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Like [animate] but also provides the animation's duration.
 * Useful when a non-time-based animation specs is used.
 * */
internal suspend fun animateWithDuration(
  initialValue: Float,
  targetValue: Float,
  initialVelocity: Float,
  animationSpec: AnimationSpec<Float> = spring(),
  onStart: (duration: Duration) -> Unit,
  block: (value: Float, velocity: Float) -> Unit,
) {
  val anim = TargetBasedAnimation(
    initialValue = initialValue,
    targetValue = targetValue,
    typeConverter = Float.VectorConverter,
    animationSpec = animationSpec,
    initialVelocity = initialVelocity,
  )
  onStart(anim.durationNanos.nanoseconds)
  animate(
    initialValue = initialValue,
    targetValue = targetValue,
    initialVelocity = initialVelocity,
    animationSpec = animationSpec,
    block = block,
  )
}
