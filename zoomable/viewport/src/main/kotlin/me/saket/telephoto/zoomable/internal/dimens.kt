package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal fun Size.roundToIntSize() =
  IntSize(width.roundToInt(), height.roundToInt())

internal operator fun Size.times(scale: ScaleFactor) =
  Size(
    width = width * scale.scaleX,
    height = height * scale.scaleY,
  )

internal fun Size.discardFractionalParts(): IntSize {
  return IntSize(width = width.toInt(), height = height.toInt())
}

internal val ScaleFactor.maxScale: Float
  get() = maxOf(scaleX, scaleY)

internal operator fun ScaleFactor.unaryMinus(): ScaleFactor =
  this * -1f

internal operator fun Offset.times(factor: ScaleFactor) =
  Offset(x = x * factor.scaleX, y = y * factor.scaleY)

internal operator fun Offset.div(factor: ScaleFactor) =
  Offset(x = x / factor.scaleX, y = y / factor.scaleY)

internal fun Rect.relativeTo(other: Rect): Rect {
  val topLeftDiff = this.topLeft - other.topLeft
  return Rect(topLeftDiff, size)
}
