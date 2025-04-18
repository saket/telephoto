@file:OptIn(ExperimentalTelephotoApi::class)

package me.saket.telephoto.zoomable.internal

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.layout.ScaleFactor
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.CoordinateSpace
import me.saket.telephoto.zoomable.RealZoomableState
import me.saket.telephoto.zoomable.SpatialOffset
import me.saket.telephoto.zoomable.SpatialRect
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomableContent
import me.saket.telephoto.zoomable.ZoomableContentTransformation
import me.saket.telephoto.zoomable.ZoomableCoordinateSystem
import me.saket.telephoto.zoomable.isUnspecified

@Stable
internal class RealZoomableCoordinateSystem(
  private val state: RealZoomableState,
) : ZoomableCoordinateSystem {

  override val contentBounds: SpatialRect by derivedStateOf {
    val boundsInViewport = state.transformUnscaledContentBoundsBy { _, transformation ->
      zoomedAndTranslatedBy(
        scale = transformation.scale,
        offset = transformation.offset,
      )
    }
    if (boundsInViewport != null) {
      SpatialRect(
        topLeft = SpatialOffset(boundsInViewport.topLeft, CoordinateSpace.Viewport),
        bottomRight = SpatialOffset(boundsInViewport.bottomRight, CoordinateSpace.Viewport),
      )
    } else {
      // Note to self: this does not use SpatialRect.Zero as a fallback value.
      // Because spatial rects are lazily resolved, a zero spatial rect in one
      // coordinate space could be resolved to a non-zero rect in another space.
      SpatialRect.Unspecified
    }
  }

  override val unscaledContentBounds: SpatialRect by derivedStateOf {
    val boundsInViewport = state.transformUnscaledContentBoundsBy { inputs, _ ->
      zoomedAndTranslatedBy(
        scale = inputs.baseZoom.value,
        offset = -(inputs.baseOffset * inputs.baseZoom.value),
      )
    }
    if (boundsInViewport != null) {
      SpatialRect(
        topLeft = SpatialOffset(boundsInViewport.topLeft, CoordinateSpace.Viewport),
        bottomRight = SpatialOffset(boundsInViewport.bottomRight, CoordinateSpace.Viewport),
      )
    } else {
      SpatialRect.Unspecified
    }
  }

  // todo: add tests for this (including the zero behavior)
  override val viewportSize: Size
    get() = state.viewportSize.takeOrElse { Size.Zero }

  override fun SpatialOffset.offsetIn(target: CoordinateSpace): Offset {
    if (this.isUnspecified) {
      // todo: add tests for this
      return Offset.Unspecified
    }
    val converter = converterIfStateIsReady()
      ?: return Offset.Unspecified

    return when (target) {
      this.space -> this.offset
      CoordinateSpace.Viewport -> converter.contentToViewport(offset)
      CoordinateSpace.ZoomableContent -> converter.viewportToContent(offset)
      else -> error("Can't convert from ${this.space} to $target")
    }
  }

  override fun SpatialRect.rectIn(target: CoordinateSpace): Rect {
    if (this.isUnspecified) {
      // todo: verify that this is okay.
      // todo: add tests for this
      // Compose UI does not have a concept of an unspecified rect so this uses a non-zero, but empty rect.
      return Rect.NonZeroButEmpty
    }

    val topLeftInTarget = this.topLeft.offsetIn(target)
    val bottomRightInTarget = this.bottomRight.offsetIn(target)

    if (topLeftInTarget.isSpecified && bottomRightInTarget.isSpecified) {
      return Rect(topLeftInTarget, bottomRightInTarget)
    } else {
      // todo: add tests for this?
      return Rect.NonZeroButEmpty
    }
  }

  private fun converterIfStateIsReady(): CoordinateSpaceConverter? {
    val stateInputs = state.currentGestureStateInputs ?: return null
    val transformation = state.contentTransformation.takeIf { it.isSpecified } ?: return null
    return CoordinateSpaceConverter(
      unscaledContentBounds = stateInputs.unscaledContentBounds,
      transformation = transformation,
    )
  }

  internal data class CoordinateSpaceConverter(
    private val unscaledContentBounds: Rect,
    private val transformation: ZoomableContentTransformation,
  ) {
    private val scale: ScaleFactor
      get() = transformation.scale

    /**
     * The content's bounds after applying the current transformation (scale and offset).
     * This represents where the content is actually drawn in the viewport.
     *
     * For example, if the content is zoomed to 2x and panned 100px right:
     * - The size will be 2x the original content size.
     * - The topLeft will be offset by 100px from the original position.
     */
    private val transformedContentBounds: Rect
      get() = unscaledContentBounds.zoomedAndTranslatedBy(scale, transformation.offset)

    // todo: if a coordinate in the viewport is outside the bounds of the image, it should be coerced in
    fun viewportToContent(offset: Offset): Offset {
      // To convert from viewport to content coordinates:
      // 1. Shift by -transformedContentBounds.topLeft (to get relative to transformed content)
      // 2. Divide by scale (to get back to unscaled coordinates)
      // 3. Shift by +unscaledContentBounds.topLeft (to get absolute coordinates)
      return (offset - transformedContentBounds.topLeft) / scale + unscaledContentBounds.topLeft
    }

    fun contentToViewport(offset: Offset): Offset {
      // To convert from content to viewport coordinates:
      // 1. Shift by -unscaledContentBounds.topLeft (to get relative to content)
      // 2. Scale by scale factor (to get scaled coordinates)
      // 3. Shift by +transformedContentBounds.topLeft (to get absolute coordinates)
      return (offset - unscaledContentBounds.topLeft) * scale + transformedContentBounds.topLeft
    }
  }
}

internal data object ContentCoordinateSpace : CoordinateSpace

internal data object ViewportCoordinateSpace : CoordinateSpace

private val Rect.Companion.NonZeroButEmpty: Rect
  get() = Rect(-1f, -1f, -1f, -1f)
