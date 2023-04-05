package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import kotlin.LazyThreadSafetyMode.NONE

internal fun Rect.topLeftCoercedInside(
  destination: Size,
  alignment: Alignment,
  layoutDirection: LayoutDirection,
): Offset {
  return coerceInside(
    destination = destination,
    targetOffset = topLeft,
    alignment = alignment,
    layoutDirection = layoutDirection
  )
}

// todo: doc.
// TODO: consider inlining this into topLeftCoercedInside().
internal fun Rect.coerceInside(
  destination: Size,
  targetOffset: Offset,
  alignment: Alignment,
  layoutDirection: LayoutDirection,
): Offset {
  val alignedOffset by lazy(NONE) {
    // Rounding of floats to ints will cause some loss in precision because the final
    // offset is calculated by combining offset & zoom, but hopefully this is okay.
    // The alternative would be to copy Alignment's code to work with floats.
    alignment.align(
      size = size.roundToIntSize(),
      space = destination.roundToIntSize(),
      layoutDirection = layoutDirection,
    )
  }

  return targetOffset.copy(
    x = if (width >= destination.width) {
      targetOffset.x.coerceIn(
        minimumValue = (destination.width - width).coerceAtMost(0f),
        maximumValue = 0f
      )
    } else {
      alignedOffset.x.toFloat()
    },
    y = if (height >= destination.height) {
      targetOffset.y.coerceIn(
        minimumValue = (destination.height - height).coerceAtMost(0f),
        maximumValue = 0f
      )
    } else {
      alignedOffset.y.toFloat()
    }
  )
}
