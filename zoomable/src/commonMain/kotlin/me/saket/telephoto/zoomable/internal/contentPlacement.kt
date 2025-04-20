package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.LayoutDirection
import me.saket.telephoto.zoomable.zoomable
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Calculate an adjusted top-left offset of this [Rect] such that it remains within the [viewportBounds].
 *
 * This is used by [Modifier.zoomable] to prevent panning of its content outside of its layout bounds.
 */
internal fun Rect.calculateTopLeftToOverlapWith(
  viewportBounds: Rect,
  alignment: Alignment,
  layoutDirection: LayoutDirection,
): Offset {
  val alignedOffset by lazy(NONE) {
    // Rounding of floats to ints will cause some loss in precision because the final
    // offset is calculated by combining offset & zoom, but hopefully this is okay.
    // The alternative would be to fork Alignment's code to work with floats.
    alignment.align(
      size = size.roundToIntSize(),
      space = viewportBounds.size.roundToIntSize(),
      layoutDirection = layoutDirection,
    )
  }

  return Offset(
    // For the horizontal axis, if the image is larger than the container, allow panning by clamping;
    // otherwise, position it according to the alignment (relative to the container's left edge).
    x = if (width > viewportBounds.width) {
      left.coerceIn(
        minimumValue = viewportBounds.right - width,
        maximumValue = viewportBounds.left,
      )
    } else {
      // When the image is smaller, use the alignment-provided offset.
      viewportBounds.left + alignedOffset.x
    },
    y = if (height > viewportBounds.height) {
      top.coerceIn(
        viewportBounds.bottom - height,
        viewportBounds.top,
      )
    } else {
      viewportBounds.top + alignedOffset.y
    },
  )
}
