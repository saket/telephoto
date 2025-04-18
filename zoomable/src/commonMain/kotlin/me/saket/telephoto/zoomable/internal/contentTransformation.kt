package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import me.saket.telephoto.zoomable.ContentOffset
import me.saket.telephoto.zoomable.ContentZoomFactor
import me.saket.telephoto.zoomable.GestureState
import me.saket.telephoto.zoomable.GestureStateInputs
import me.saket.telephoto.zoomable.ZoomableContentTransformation

internal data class RealZoomableContentTransformation(
  override val isSpecified: Boolean,
  override val scale: ScaleFactor,
  override val scaleMetadata: ScaleMetadata,
  override val offset: Offset,
  override val centroid: Offset?,
  @Deprecated("deprecated in the interface") override val contentSize: Size,
  override val rotationZ: Float = 0f,
) : ZoomableContentTransformation {

  data class ScaleMetadata(
    override val initialScale: ScaleFactor,
    override val userZoom: Float,
  ) : ZoomableContentTransformation.ScaleMetadata

  companion object {
    fun calculateFrom(
      gestureStateInputs: GestureStateInputs,
      gestureState: GestureState,
    ): ZoomableContentTransformation {
      val contentZoom = ContentZoomFactor(
        baseZoom = gestureStateInputs.baseZoom,
        userZoom = gestureState.userZoom,
      )
      val contentOffset = ContentOffset(
        baseOffset = gestureStateInputs.baseOffset,
        userOffset = gestureState.userOffset,
      )
      val contentSize = gestureStateInputs.unscaledContentBounds.size
      return RealZoomableContentTransformation(
        isSpecified = true,
        contentSize = contentSize,
        scale = contentZoom.finalZoom(),
        scaleMetadata = ScaleMetadata(
          initialScale = gestureStateInputs.baseZoom.value,
          userZoom = gestureState.userZoom.value,
        ),
        offset = (-contentOffset.finalOffset() * contentZoom.finalZoom()).let {
          // Make it easier for consumers to perform `if (offset == zero)` checks.
          if (it == -Offset.Zero) Offset.Zero else it
        },
        centroid = gestureState.lastCentroid,
      )
    }
  }
}
