package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal fun Size.roundToIntSize(): IntSize {
  return IntSize(width.roundToInt(), height.roundToInt())
}

internal operator fun Size.times(scale: ScaleFactor): Size {
  return Size(
    width = width * scale.scaleX,
    height = height * scale.scaleY,
  )
}
