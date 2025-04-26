package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.layout.ScaleFactor
import me.saket.telephoto.zoomable.AbsoluteOffset
import me.saket.telephoto.zoomable.AbsoluteZoomFactor
import me.saket.telephoto.zoomable.GestureState
import me.saket.telephoto.zoomable.GestureStateInputs
import me.saket.telephoto.zoomable.ZoomableState

/**
 * Used when [ZoomableState]'s saved gesture state cannot be restored due to viewport size changes.
 * Adjusts zoom and pan values to maintain the content's centroid position in the new viewport.
 */
internal class GestureStateAdjuster(
  private val oldFinalZoom: ScaleFactor,
  private val oldContentOffsetAtViewportCenter: Offset, // Present in the content's coordinate space.
) {

  fun adjustForNewViewportSize(
    inputs: GestureStateInputs,
    coerceWithinBounds: (AbsoluteOffset, AbsoluteZoomFactor) -> AbsoluteOffset,
  ): GestureState {
    // Retain the same zoom level. This will change the user zoom level, but that's okay.
    // Switching from a smaller to a larger screen should display more content, not the same.
    val newZoom = AbsoluteZoomFactor.forFinalZoom(inputs.baseZoom, finalZoom = oldFinalZoom)

    // todo: can SpatialOffset be used here?
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
