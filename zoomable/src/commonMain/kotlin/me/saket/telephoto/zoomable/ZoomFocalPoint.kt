package me.saket.telephoto.zoomable

import androidx.compose.ui.geometry.Offset
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.ZoomFocalPoint.Companion.moveToViewportCenter
import me.saket.telephoto.zoomable.ZoomFocalPoint.Companion.viewportCenter
import me.saket.telephoto.zoomable.ZoomFocalPoint.Companion.zoomAround
import me.saket.telephoto.zoomable.internal.maxScale
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.spatial.SpatialOffset
import kotlin.math.abs

/**
 * Defines the focal point to use when zooming. The focal point determines which part of the
 * content remains fixed (or is brought into position) as the zoom animation runs.
 *
 * Available options:
 *
 * - [ZoomFocalPoint.zoomAround][zoomAround] (default)
 * - [ZoomFocalPoint.moveToViewportCenter][moveToViewportCenter]
 * - [ZoomFocalPoint.viewportCenter][viewportCenter]
 * */
@ExperimentalTelephotoApi
abstract class ZoomFocalPoint internal constructor() {
  companion object {
    /**
     * Zoom around `centroid` as the pivot for zooming. When you zoom in or out, this
     * point stays exactly where it is on your screen, and everything else moves around it.
     * This is the default anchor used when you're zooming using a pinch gesture or
     * a double tap.
     */
    fun zoomAround(centroid: SpatialOffset): ZoomFocalPoint =
      ZoomAroundCentroid(centroid)

    /** No panning. Zooms around the center of the viewport. */
    fun viewportCenter(): ZoomFocalPoint =
      ZoomAroundCentroid(SpatialOffset.Unspecified)

    /**
     * Zoom while moving `newCenter` to the center of the viewport.
     */
    fun moveToViewportCenter(newCenter: SpatialOffset): ZoomFocalPoint =
      MoveToCenter(newCenter)
  }

  internal abstract fun computeCentroid(
    state: ZoomableState,
    targetZoomFactor: Float
  ): SpatialOffset
}

@OptIn(ExperimentalTelephotoApi::class)
private data class ZoomAroundCentroid(val centroid: SpatialOffset) : ZoomFocalPoint() {
  override fun computeCentroid(state: ZoomableState, targetZoomFactor: Float) = centroid
}

@OptIn(ExperimentalTelephotoApi::class)
private data class MoveToCenter(val newCenter: SpatialOffset) : ZoomFocalPoint() {
  override fun computeCentroid(state: ZoomableState, targetZoomFactor: Float): SpatialOffset {
    val transformation = state.contentTransformation
    check(transformation.isSpecified) { "called before the content is ready?" }

    with(state.coordinateSystem) {
      val centerInViewport = newCenter.offsetIn(CoordinateSpace.Viewport)
      val viewportCenter = contentBounds.rectIn(CoordinateSpace.Viewport).center

      val targetCentroid = calculateCentroidToMovePointToTarget(
        point = centerInViewport,
        target = viewportCenter,
        currentZoom = transformation.scale.maxScale,
        targetZoom = targetZoomFactor,
      )
      return SpatialOffset(targetCentroid, CoordinateSpace.Viewport)
    }
  }

  /** Calculates a centroid to make a specific [point] move to the [target] position after zooming. */
  private fun calculateCentroidToMovePointToTarget(
    point: Offset,
    target: Offset,
    currentZoom: Float,
    targetZoom: Float,
  ): Offset {
    val zoomRatio = targetZoom / currentZoom
    if (abs(zoomRatio - 1f) < ZoomDeltaEpsilon) {
      // No zoom change, any centroid will work.
      return target
    }

    // Given that I want a point to end up at its target position after zooming by
    // `zoomRatio`, what centroid should I zoom around? The math:
    //
    // - After zoom: point_new = centroid + (point_old - centroid) * zoomRatio
    // - I want: point_new = target
    //   - Solving: target = centroid + (point - centroid) * zoomRatio
    // - Therefore: centroid = (target - point * zoomRatio) / (1 - zoomRatio)
    return (target - point * zoomRatio) / (1f - zoomRatio)
  }
}
