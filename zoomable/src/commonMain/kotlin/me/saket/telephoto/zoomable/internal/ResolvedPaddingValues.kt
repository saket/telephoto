package me.saket.telephoto.zoomable.internal

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

internal data class ResolvedPaddingValues(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
) {
  val topLeft: Offset
    get() = Offset(left, top)

  val size: Size
    get() = Size(width = left + right, height = top + bottom)
}

internal fun PaddingValues.resolve(density: Density, layoutDirection: LayoutDirection): ResolvedPaddingValues {
  return with(density) {
    ResolvedPaddingValues(
      left = calculateStartPadding(layoutDirection).toPx(),
      top = calculateTopPadding().toPx(),
      right = calculateEndPadding(layoutDirection).toPx(),
      bottom = calculateBottomPadding().toPx(),
    )
  }
}
