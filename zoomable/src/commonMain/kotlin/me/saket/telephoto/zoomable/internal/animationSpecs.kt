package me.saket.telephoto.zoomable.internal

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

internal fun <T> SpringSpec<T>.copy(visibilityThreshold: T): SpringSpec<T> {
  return spring<T>(
    dampingRatio = this.dampingRatio,
    stiffness = this.stiffness,
    visibilityThreshold = visibilityThreshold,
  )
}
