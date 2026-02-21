package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.layout.ScaleFactor
import me.saket.telephoto.zoomable.AbsoluteOffset
import me.saket.telephoto.zoomable.AbsoluteZoomFactor
import me.saket.telephoto.zoomable.GestureState
import me.saket.telephoto.zoomable.GestureStateInputs
import me.saket.telephoto.zoomable.ZoomableState

/**
 * Used when [ZoomableState]'s content size changes (e.g., when swapping from low-res to high-res image).
 * Adjusts pan values to maintain the content's centroid position in the viewport.
 */
internal class ContentSizeAdjuster(
  private val oldContentSize: Size,
  private val oldFinalZoom: ScaleFactor,
  private val oldContentOffsetAtViewportCenter: Offset, // Present in the content's coordinate space.
) {

  fun adjustForNewContentSize(
    inputs: GestureStateInputs,
    coerceWithinBounds: (AbsoluteOffset, AbsoluteZoomFactor) -> AbsoluteOffset,
  ): GestureState {
    // Retain the same zoom level. This will change the user zoom level, but that's okay.
    // The visual zoom should remain consistent when the content size changes.
    val newZoom = AbsoluteZoomFactor.forFinalZoom(inputs.baseZoom, finalZoom = oldFinalZoom)

    // Find the offset needed to move the old anchor (i.e., the content offset at the viewport
    // center) back to the viewport's center. The anchor is present in the content's coordinate
    // space so it will be be transformed to the viewport space for the scope of this calculation.
    val newUserOffset = oldContentOffsetAtViewportCenter.withZoom(newZoom.finalZoom()) { anchorInViewportSpace ->
      anchorInViewportSpace - inputs.viewportSize.center
    }
    val proposedAbsoluteOffset = AbsoluteOffset.forFinalOffset(
      baseOffset = inputs.baseOffset,
      finalOffset = newUserOffset,
    )

    return GestureState(
      userOffset = coerceWithinBounds(proposedAbsoluteOffset, newZoom).userOffset,
      userZoom = newZoom.userZoom,
      lastCentroid = inputs.viewportSize.center
    )
  }
}
