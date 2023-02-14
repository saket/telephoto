package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.math.roundToInt

internal fun Rect.topLeftCoercedInside(viewport: Rect, alignment: Alignment): Offset {
  return coerceInside(viewport, targetOffset = topLeft, alignment = alignment)
}

// TODO: consider inlining this into topLeftCoercedInside().
internal fun Rect.coerceInside(
  viewport: Rect,
  targetOffset: Offset,
  alignment: Alignment,
): Offset {
  val alignedOffset by lazy(NONE) {
    // Rounding of floats to ints will cause some loss in precision because the final
    // offset is calculated by combining offset & zoom, but hopefully this is okay.
    // The alternative would be to copy Alignment's code to work with floats.
    alignment.align(
      size = size.roundToIntSize(),
      space = viewport.size.roundToIntSize(),
      layoutDirection = LayoutDirection.Ltr
    )
  }

  return targetOffset.copy(
    x = if (width >= viewport.width) {
      targetOffset.x.coerceIn(
        minimumValue = (viewport.width - width).coerceAtMost(0f),
        maximumValue = 0f
      )
    } else {
      alignedOffset.x.toFloat()
    },
    y = if (height >= viewport.height) {
      targetOffset.y.coerceIn(
        minimumValue = (viewport.height - height).coerceAtMost(0f),
        maximumValue = 0f
      )
    } else {
      alignedOffset.y.toFloat()
    }
  )
}
