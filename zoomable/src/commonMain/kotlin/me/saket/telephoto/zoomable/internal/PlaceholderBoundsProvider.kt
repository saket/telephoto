package me.saket.telephoto.zoomable.internal

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.spatial.isSpecified
import kotlin.jvm.JvmInline

/**
 * Used by [me.saket.telephoto.zoomable.ZoomableImage] to provide a fallback value for
 * [ZoomableState.transformedContentBounds] before the full quality image is loaded. This
 * ensures that the bounds aren't empty while a placeholder image is visible.
 */
@JvmInline
@OptIn(ExperimentalTelephotoApi::class)
internal value class PlaceholderBoundsProvider(
  private val placeholderState: ZoomableState,
) {
  @Stable
  fun calculate(): Rect? {
    return with(placeholderState.coordinateSystem) {
      val bounds = unscaledContentBounds.takeIf { it.isSpecified } ?: return null
      bounds.rectIn(CoordinateSpace.Viewport)
    }
  }
}
