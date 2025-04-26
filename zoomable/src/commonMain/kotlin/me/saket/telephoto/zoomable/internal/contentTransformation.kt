package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import me.saket.telephoto.zoomable.AbsoluteOffset
import me.saket.telephoto.zoomable.AbsoluteZoomFactor
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
    val Unspecified = RealZoomableContentTransformation(
      isSpecified = false,
      contentSize = Size.Zero,
      scale = ScaleFactor.Zero,  // Effectively hide the content until an initial zoom value is calculated.
      scaleMetadata = ScaleMetadata(
        initialScale = ScaleFactor.Zero,
        userZoom = 0f,
      ),
      offset = Offset.Zero,
      centroid = null,
    )

    fun calculateFrom(
      gestureStateInputs: GestureStateInputs,
      gestureState: GestureState,
    ): ZoomableContentTransformation {
      val absoluteZoom = AbsoluteZoomFactor(
        baseZoom = gestureStateInputs.baseZoom,
        userZoom = gestureState.userZoom,
      )
      val absoluteOffset = AbsoluteOffset(
        baseOffset = gestureStateInputs.baseOffset,
        userOffset = gestureState.userOffset,
      )
      val contentSize = gestureStateInputs.unscaledContentBounds.size
      return RealZoomableContentTransformation(
        isSpecified = true,
        contentSize = contentSize,
        scale = absoluteZoom.finalZoom(),
        scaleMetadata = ScaleMetadata(
          initialScale = gestureStateInputs.baseZoom.value,
          userZoom = gestureState.userZoom.value,
        ),
        offset = (-absoluteOffset.finalOffset() * absoluteZoom.finalZoom()).let {
          // Make it easier for consumers to perform `if (offset == zero)` checks.
          if (it == -Offset.Zero) Offset.Zero else it
        },
        centroid = gestureState.lastCentroid,
      )
    }
  }
}
