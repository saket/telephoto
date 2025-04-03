@file:OptIn(ExperimentalTelephotoApi::class)

package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import me.saket.telephoto.zoomable.CoordinateSpace
import me.saket.telephoto.zoomable.CoordinateSystem
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.RealZoomableState
import me.saket.telephoto.zoomable.SpatialOffset
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomableContent
import me.saket.telephoto.zoomable.ZoomableContentTransformation
import me.saket.telephoto.zoomable.ZoomableState
import kotlin.jvm.JvmInline

@JvmInline
internal value class ZoomableCoordinateSystem(
  private val state: ZoomableState,
) : CoordinateSystem {

  override fun SpatialOffset.offsetIn(target: CoordinateSpace): Offset {
    return when (target) {
      this.space -> this.offset
      CoordinateSpace.Viewport -> converter().contentToViewport(offset)
      CoordinateSpace.ZoomableContent -> converter().viewportToContent(offset)
      else -> error("Can't convert from ${this.space} to $target")
    }
  }

  private fun converter(): CoordinateSpaceConverter {
    check(state is RealZoomableState)
    check(state.isReadyForInteraction) {
      "Modifier.zoomable() hasn't measured its content yet"
    }
    return CoordinateSpaceConverter(
      unscaledContentBounds = state.currentGestureStateInputs!!.unscaledContentBounds,
      transformation = state.contentTransformation,
    )
  }

  internal data class CoordinateSpaceConverter(
    private val unscaledContentBounds: Rect,
    private val transformation: ZoomableContentTransformation,
  ) {
    private val scale get() = transformation.scale

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

