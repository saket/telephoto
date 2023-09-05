package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.min

internal fun IntSize.coerceIn(min: IntSize, max: IntSize): IntSize {
  return IntSize(
    width = width.coerceIn(minimumValue = min.width, maximumValue = max.width),
    height = height.coerceIn(minimumValue = min.height, maximumValue = max.height),
  )
}

internal fun Size.discardFractionalParts(): IntSize {
  return IntSize(width = width.toInt(), height = height.toInt())
}

internal val IntSize.minDimension: Int
  get() = min(width.absoluteValue, height.absoluteValue)

internal fun Offset.discardFractionalParts(): IntOffset {
  return IntOffset(x = x.toInt(), y = y.toInt())
}

internal fun IntRect.contains(offset: Offset?): Boolean {
  if (offset == null) {
    return false
  } else {
    return contains(offset.discardFractionalParts())
  }
}

internal fun Float.toCeilInt(): Int {
  return ceil(this).toInt()
}

internal fun IntRect.scaledAndOffsetBy(scale: ScaleFactor, offset: Offset): Rect {
  return Rect(
    left = (left * scale.scaleX) + offset.x,
    right = (right * scale.scaleX) + offset.x,
    top = (top * scale.scaleY) + offset.y,
    bottom = (bottom * scale.scaleY) + offset.y,
  )
}

internal fun Rect.discardFractionalValues(): IntRect {
  return IntRect(
    left = left.toInt(),
    right = right.toInt(),
    top = top.toInt(),
    bottom = bottom.toInt(),
  )
}

/**
 * Equivalent to `Rect#overlaps(Rect(Offset.Zero, size))`.
 *
 * Copied from [Rect.overlaps]
 */
@Suppress("RedundantIf")
internal fun Rect.overlaps(other: IntSize): Boolean {
  if (right <= 0 || other.width <= left)
    return false
  if (bottom <= 0 || other.height <= top)
    return false
  return true
}

internal val ScaleFactor.maxScale: Float
  get() = maxOf(scaleX, scaleY)
