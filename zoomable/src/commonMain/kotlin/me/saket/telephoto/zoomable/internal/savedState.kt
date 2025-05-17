@file:Suppress("DataClassPrivateConstructor")

package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.layout.ScaleFactor
import dev.icerock.moko.parcelize.Parcel
import dev.icerock.moko.parcelize.Parcelable
import dev.icerock.moko.parcelize.Parceler
import dev.icerock.moko.parcelize.Parcelize
import dev.icerock.moko.parcelize.TypeParceler
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

@Parcelize
internal data class SavedZoomableState(
  val autoApplyTransformations: Boolean,
  val gestureState: SavedGestureState? = null,
) : Parcelable

@Parcelize
@TypeParceler<Offset, OffsetParceler>
internal data class SavedGestureState(
  private val userOffset: Offset,
  private val userZoom: Float,
  private val centroid: Offset,
  private val contentPositionInfo: ContentPositionInfo?,
) : Parcelable {

  @Parcelize
  @TypeParceler<Size, SizeParceler>
  @TypeParceler<Offset, OffsetParceler>
  @TypeParceler<ScaleFactor, ScaleFactorParceler>
  data class ContentPositionInfo(
    val viewportSize: Size,
    val contentOffsetAtViewportCenter: Offset,  // Present in the content's coordinate space.
    val finalZoomFactor: ScaleFactor,
  ) : Parcelable

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
        userOffset = gestureState.userOffset.value,
        userZoom = gestureState.userZoom.value,
        centroid = gestureState.lastCentroid,
        contentPositionInfo = inputs.viewportSize.let { viewportSize ->
          if (viewportSize.isSpecifiedAndNonEmpty) {
            ContentPositionInfo(
              viewportSize = viewportSize,
              contentOffsetAtViewportCenter = with(state.coordinateSystem) {
                val viewportCenter = SpatialOffset(
                  offset = viewportSize.center,
                  space = CoordinateSpace.Viewport,
                )
                viewportCenter.offsetIn(CoordinateSpace.ZoomableContent)
              },
              finalZoomFactor = AbsoluteZoomFactor(
                baseZoom = inputs.baseZoom,
                userZoom = gestureState.userZoom,
              ).finalZoom(),
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
    val wasGestureStateEmpty = userOffset == Offset.Zero && (userZoom - 1f) < ZoomDeltaEpsilon
    if (
      wasGestureStateEmpty
      || (contentPositionInfo == null || contentPositionInfo.viewportSize == inputs.viewportSize)
    ) {
      return GestureState(
        userOffset = UserOffset(userOffset),
        userZoom = UserZoomFactor(userZoom),
        lastCentroid = centroid,
      )
    }

    // If the viewport size changes after state restoration (likely due to orientation change or
    // window resize), the content's _visual_ anchor needs to be restored to its original position.
    // Treat the content offset at the viewport's center as the anchor and adjust the gesture state
    // to maintain the anchor's position in the new viewport.
    val stateAdjuster = GestureStateAdjuster(
      oldFinalZoom = contentPositionInfo.finalZoomFactor,
      oldContentOffsetAtViewportCenter = contentPositionInfo.contentOffsetAtViewportCenter,
    )
    return stateAdjuster.adjustForNewViewportSize(
      inputs = inputs,
      coerceWithinBounds = coerceOffsetWithinBounds,
    )
  }
}

private object OffsetParceler : Parceler<Offset> {
  override fun create(parcel: Parcel) =
    Offset(x = parcel.readFloat(), y = parcel.readFloat())

  override fun Offset.write(parcel: Parcel, flags: Int) {
    parcel.writeFloat(x)
    parcel.writeFloat(y)
  }
}

private object SizeParceler : Parceler<Size> {
  override fun create(parcel: Parcel) =
    Size(width = parcel.readFloat(), height = parcel.readFloat())

  override fun Size.write(parcel: Parcel, flags: Int) {
    parcel.writeFloat(width)
    parcel.writeFloat(height)
  }
}

private object ScaleFactorParceler : Parceler<ScaleFactor> {
  override fun create(parcel: Parcel) =
    ScaleFactor(scaleX = parcel.readFloat(), scaleY = parcel.readFloat())

  override fun ScaleFactor.write(parcel: Parcel, flags: Int) {
    parcel.writeFloat(scaleX)
    parcel.writeFloat(scaleY)
  }
}
