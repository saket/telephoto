@file:OptIn(ExperimentalTelephotoApi::class)

package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.ExperimentalTelephotoApi

/**
 * A 2D offset bound to a specific [CoordinateSpace] inside a [CoordinateSystem].
 *
 * `SpatialOffset` ensures that geometric data is always contextualized, preventing miscalculations
 * across incompatible coordinate spaces (e.g., viewport vs. image space).
 *
 * For reading the offset in a space, use [SpatialOffset.offsetIn][CoordinateSystem.offsetIn] with
 * a [CoordinateSystem] receiver:
 *
 * ```kotlin
 * val offsetInViewport = SpatialOffset(
 *   offset = Offset(100f, 200f),
 *   space = CoordinateSpace.Viewport,
 * )
 *
 * val offsetInImage: Offset = with(zoomableState.coordinateSystem) {
 *   offsetInViewport.offsetIn(CoordinateSpace.ZoomableContent)
 * }
 * ```
 */
@Poko
@Immutable
@ExperimentalTelephotoApi
class SpatialOffset(
  internal val offset: Offset,
  val space: CoordinateSpace,
) {

  companion object {
    val Unspecified: SpatialOffset
      get() = SpatialOffset(Offset.Unspecified, CoordinateSpace.Unspecified)
  }
}

/**
 * A 2D rectangle bound to a specific [CoordinateSpace] inside a [CoordinateSystem].
 *
 * Like [SpatialOffset], `SpatialRect` ensures that geometric data remains contextualized,
 * preventing miscalculations across incompatible coordinate spaces (e.g., viewport vs. image space).
 *
 * For reading the bounds or its size in a given space, use [SpatialRect.rectIn][CoordinateSystem.rectIn]
 * or [SpatialRect.sizeIn][CoordinateSystem.sizeIn] with a [CoordinateSystem] receiver:
 *
 * ```kotlin
 * val spatialRect = SpatialRect(
 *   topLeft = SpatialOffset(Offset(0f, 0f), CoordinateSpace.Viewport),
 *   bottomRight = SpatialOffset(Offset(200f, 300f), CoordinateSpace.Viewport),
 * )
 *
 * val imageRect: Rect = with(zoomableState.coordinateSystem) {
 *   spatialRect.rectIn(CoordinateSpace.ZoomableContent)
 * }
 * ```
 */
@Poko
@Immutable
@ExperimentalTelephotoApi
class SpatialRect(
  val topLeft: SpatialOffset,
  val bottomRight: SpatialOffset,
) {

  companion object {
    val Unspecified: SpatialRect
      get() = SpatialRect(SpatialOffset.Unspecified, SpatialOffset.Unspecified)
  }
}

/**
 * Identifies a coordinate space (e.g., viewport or zoomable image) that provides context
 * to [SpatialOffset] values. The conversion between coordinate spaces is provided by a
 * [CoordinateSystem].
 */
@ExperimentalTelephotoApi
interface CoordinateSpace {
  companion object {
    val Unspecified: CoordinateSpace = object : CoordinateSpace {}
  }
}

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
  fun SpatialRect.sizeIn(target: CoordinateSpace): Size
}

/** `false` when this is [SpatialOffset.Unspecified]. */
@Stable
val SpatialOffset.isSpecified: Boolean
  get() = offset.isSpecified

/** `true` when this is [SpatialOffset.Unspecified]. */
@Stable
val SpatialOffset.isUnspecified: Boolean
  get() = offset.isUnspecified

/**
 * If this [Offset] [isSpecified] then this is returned, otherwise [block] is executed
 * and its result is returned.
 */
inline fun SpatialOffset.takeOrElse(block: () -> SpatialOffset): SpatialOffset =
  if (isSpecified) this else block()

/** `false` when this is [SpatialRect.Unspecified]. */
@Stable
val SpatialRect.isSpecified: Boolean
  get() = topLeft.isSpecified && bottomRight.isSpecified

/** `true` when this is [SpatialRect.Unspecified]. */
@Stable
val SpatialRect.isUnspecified: Boolean
  get() = topLeft.isUnspecified || bottomRight.isUnspecified
