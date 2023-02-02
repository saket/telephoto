package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

internal fun Rect.topLeftCoercedInside(viewport: Rect): Offset {
  return coerceInside(viewport, targetOffset = topLeft)
}

// TODO: inline this if it's not needed.
internal fun Rect.coerceInside(viewport: Rect, targetOffset: Offset): Offset {
  return targetOffset.copy(
    x = targetOffset.x.coerceIn(
      minimumValue = (viewport.width - width).coerceAtMost(0f),
      maximumValue = 0f
    ),
    y = targetOffset.y.coerceIn(
      minimumValue = (viewport.height - height).coerceAtMost(0f),
      maximumValue = 0f
    )
  )
}
