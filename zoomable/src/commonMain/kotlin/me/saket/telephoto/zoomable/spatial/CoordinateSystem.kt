package me.saket.telephoto.zoomable.spatial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import me.saket.telephoto.ExperimentalTelephotoApi

/**
 * Represents a system that understands its coordinate spaces and how spatial offsets
 * are interpreted and transformed across them.
 *
 * Implementations can map positions between spaces (such as viewport and image),
 * typically using transformation data like scale & pan values.
 */
@ExperimentalTelephotoApi
interface CoordinateSystem {
  /**
   * Converts this [SpatialOffset] to a raw [Offset] in the `target` coordinate space.
   *
   * @return the resolved offset, or [Offset.Unspecified] if the spatial offset is unspecified
   *         or if the `target` coordinate space has not yet been measured.
   */
  fun SpatialOffset.offsetIn(target: CoordinateSpace): Offset

  /**
   * Convert this [SpatialRect] to a raw [Rect] in the `target` coordinate space.
   *
   * @return the resolved rect, or an empty rect if the spatial rect is unspecified
   *         or if the `target` coordinate space has not yet been measured.
   */
  fun SpatialRect.rectIn(target: CoordinateSpace): Rect

  /**
   * Resolve this [SpatialRect]'s size in the `target` coordinate space.
   *
   * @return the resolved size, or [Size.Unspecified] if the spatial rect is unspecified
   *         or if the `target` coordinate space has not yet been measured.
   */
  fun SpatialRect.sizeIn(target: CoordinateSpace): Size {
    return rectIn(target).size
  }
}
