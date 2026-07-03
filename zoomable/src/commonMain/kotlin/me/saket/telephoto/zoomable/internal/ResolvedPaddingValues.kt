package me.saket.telephoto.zoomable.internal

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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

/**
 * Return this rect after applying [padding] inward.
 *
 * When opposite padding values meet or overlap, the padded bounds collapse to their midpoint on that
 * axis instead of becoming negative-sized. This keeps large padding values usable for intentionally
 * collapsing the viewport to a center line or point.
 */
internal fun Rect.padded(padding: ResolvedPaddingValues): Rect {
  val (boundedLeft, boundedRight) = collapseCrossedEdgesToCenter(
    start = left + padding.left,
    end = right - padding.right,
  )
  val (boundedTop, boundedBottom) = collapseCrossedEdgesToCenter(
    start = top + padding.top,
    end = bottom - padding.bottom,
  )
  return Rect(
    left = boundedLeft,
    top = boundedTop,
    right = boundedRight,
    bottom = boundedBottom,
  )
}

/**
 * Returns edges that never cross each other.
 *
 * Crossed edges mean padding consumed more than the available size. In that case, collapse the
 * bounds to the midpoint so downstream zoom/pan math sees a zero-sized axis instead of inverted
 * bounds.
 */
private fun collapseCrossedEdgesToCenter(start: Float, end: Float): Pair<Float, Float> {
  return if (start <= end) {
    start to end
  } else {
    val center = (start + end) / 2f
    center to center
  }
}
