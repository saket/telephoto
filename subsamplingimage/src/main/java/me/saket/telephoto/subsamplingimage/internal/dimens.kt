package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.roundToInt

internal fun Size.coerceAtLeast(other: Size): Size {
  return Size(
    width = width.coerceAtLeast(other.width),
    height = height.coerceAtLeast(other.height)
  )
}

internal fun Size.coerceAtMost(other: Size): Size {
  return Size(
    width = width.coerceAtMost(other.width),
    height = height.coerceAtMost(other.height)
  )
}

internal fun Size.discardFractionalParts(): IntSize {
  return IntSize(width = width.toInt(), height = height.toInt())
}

internal fun Size.toCeilIntSize(): IntSize {
  return IntSize(width = ceil(width).roundToInt(), height = ceil(height).roundToInt())
}

internal fun Offset.toCeilIntOffset(): IntOffset {
  return IntOffset(ceil(x).roundToInt(), ceil(y).roundToInt())
}

internal fun Float.toCeilInt(): Int {
  return ceil(this).toInt()
}

internal fun Rect.scaledAndOffsetBy(scale: ScaleFactor, offset: Offset): Rect {
  return copy(
    left = (left * scale.scaleX) + offset.x,
    right = (right * scale.scaleX) + offset.x,
    top = (top * scale.scaleY) + offset.y,
    bottom = (bottom * scale.scaleY) + offset.y,
  )
}
