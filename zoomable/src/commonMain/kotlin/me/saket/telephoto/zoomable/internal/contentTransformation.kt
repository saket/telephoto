@file:OptIn(ExperimentalTelephotoApi::class)

package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.*
import me.saket.telephoto.zoomable.spatial.CoordinateSpace

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
      coordinateSystem: RealZoomableCoordinateSystem,
    ): ZoomableContentTransformation {
      val zoom = with(coordinateSystem) {
        gestureState.userZoom.value.scaleIn(CoordinateSpace.ZoomableContent)
      }

      //println("zoom = ${zoom.maxScale} (spatial = ${gestureState.userZoom.value})")

      val absoluteOffset = AbsoluteOffset(
        baseOffset = gestureStateInputs.baseOffset,
        userOffset = gestureState.userOffset,
      )
      val contentSize = gestureStateInputs.unscaledContentBounds.size
      return RealZoomableContentTransformation(
        isSpecified = true,
        contentSize = contentSize,
        scale = zoom,
        scaleMetadata = ScaleMetadata(
          initialScale = gestureStateInputs.baseZoom.value,
          userZoom = with(coordinateSystem) {
            // todo: is this coordinate space correct?
            gestureState.userZoom.value.scaleIn(CoordinateSpace.Viewport).maxScale
          },
        ),
        offset = (-absoluteOffset.finalOffset() * zoom).let {
          // Make it easier for consumers to perform `if (offset == zero)` checks.
          if (it == -Offset.Zero) Offset.Zero else it
        },
        centroid = gestureState.lastCentroid,
      )
    }
  }
}
