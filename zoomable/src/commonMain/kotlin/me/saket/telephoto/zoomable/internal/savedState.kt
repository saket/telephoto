@file:Suppress("DataClassPrivateConstructor")

package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.util.packFloats
import androidx.compose.ui.util.unpackFloat1
import androidx.compose.ui.util.unpackFloat2
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.AbsoluteOffset
import me.saket.telephoto.zoomable.AbsoluteZoomFactor
import me.saket.telephoto.zoomable.GestureState
import me.saket.telephoto.zoomable.GestureStateInputs
import me.saket.telephoto.zoomable.RealZoomableState
import me.saket.telephoto.zoomable.UserOffset
import me.saket.telephoto.zoomable.UserZoomFactor
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomDeltaEpsilon
import me.saket.telephoto.zoomable.ZoomableContent
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.spatial.SpatialOffset

@AndroidParcelize
internal data class SavedZoomableState(
  val autoApplyTransformations: Boolean,
  val gestureState: SavedGestureState? = null,
) : AndroidParcelable

@AndroidParcelize
internal data class SavedGestureState(
  private val userOffset: Long,
  private val userZoom: Float,
  private val centroid: Long,
  private val contentPositionInfo: ContentPositionInfo?,
) : AndroidParcelable {

  @AndroidParcelize
  data class ContentPositionInfo(
    val viewportSize: Long,
    val contentOffsetAtViewportCenter: Long,  // Present in the content's coordinate space.
    val finalZoomFactor: Long,
  ) : AndroidParcelable

  @OptIn(ExperimentalTelephotoApi::class)
  companion object {
    fun from(state: RealZoomableState): SavedGestureState? {
      val inputs = state.currentGestureStateInputs ?: return null
      val gestureState = state.gestureState.calculate(inputs).let { gestureState ->
        // Touch events are canceled on state restoration.
        // If the content is over-zoomed, snap back to its zoom limits.
        gestureState.copy(
          userZoom = AbsoluteZoomFactor(inputs.baseZoom, gestureState.userZoom)
            .coerceUserZoomIn(state.zoomSpec.range)
            .userZoom
        )
      }

      return SavedGestureState(
        userOffset = gestureState.userOffset.value.packToLong(),
        userZoom = gestureState.userZoom.value,
        centroid = gestureState.lastCentroid.packToLong(),
        contentPositionInfo = inputs.viewportSize.let { viewportSize ->
          if (viewportSize.isSpecifiedAndNonEmpty) {
            ContentPositionInfo(
              viewportSize = viewportSize.packToLong(),
              contentOffsetAtViewportCenter = with(state.coordinateSystem) {
                val viewportCenter = SpatialOffset(
                  offset = viewportSize.center,
                  space = CoordinateSpace.Viewport,
                )
                viewportCenter.offsetIn(CoordinateSpace.ZoomableContent)
              }.packToLong(),
              finalZoomFactor = AbsoluteZoomFactor(
                baseZoom = inputs.baseZoom,
                userZoom = gestureState.userZoom,
              ).finalZoom().packToLong(),
            )
          } else {
            null
          }
        },
      )
    }
  }

  fun restore(
    inputs: GestureStateInputs,
    coerceOffsetWithinBounds: (AbsoluteOffset, AbsoluteZoomFactor) -> AbsoluteOffset,
  ): GestureState {
    val restoredUserOffset = userOffset.unpackAsOffset()
    val wasGestureStateEmpty = restoredUserOffset == Offset.Zero && (userZoom - 1f) < ZoomDeltaEpsilon
    if (
      wasGestureStateEmpty
      || (contentPositionInfo == null || contentPositionInfo.viewportSize.unpackAsSize() == inputs.viewportSize)
    ) {
      return GestureState(
        userOffset = UserOffset(restoredUserOffset),
        userZoom = UserZoomFactor(userZoom),
        lastCentroid = centroid.unpackAsOffset(),
      )
    }

    // If the viewport size changes after state restoration (likely due to orientation change or
    // window resize), the content's _visual_ anchor needs to be restored to its original position.
    // Treat the content offset at the viewport's center as the anchor and adjust the gesture state
    // to maintain the anchor's position in the new viewport.
    val stateAdjuster = GestureStateAdjuster(
      oldFinalZoom = contentPositionInfo.finalZoomFactor.unpackAsScaleFactor(),
      oldContentOffsetAtViewportCenter = contentPositionInfo.contentOffsetAtViewportCenter.unpackAsOffset(),
    )
    return stateAdjuster.adjustForNewViewportSize(
      inputs = inputs,
      coerceWithinBounds = coerceOffsetWithinBounds,
    )
  }
}

private fun Offset.packToLong(): Long =
  packFloats(x, y)

private fun Size.packToLong(): Long =
  packFloats(width, height)

private fun ScaleFactor.packToLong(): Long =
  packFloats(scaleX, scaleY)

private fun Long.unpackAsOffset(): Offset =
  Offset(x = unpackFloat1(this), y = unpackFloat2(this))

private fun Long.unpackAsSize(): Size =
  Size(width = unpackFloat1(this), height = unpackFloat2(this))

private fun Long.unpackAsScaleFactor(): ScaleFactor =
  ScaleFactor(scaleX = unpackFloat1(this), scaleY = unpackFloat2(this))
