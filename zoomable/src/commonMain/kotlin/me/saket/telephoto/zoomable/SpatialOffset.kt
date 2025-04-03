@file:OptIn(ExperimentalTelephotoApi::class)

package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.ExperimentalTelephotoApi

/**
 * A 2D offset bound to a specific [CoordinateSpace] inside a [CoordinateSystem].
 *
 * `SpatialOffset` ensures that positional data is always contextualized, preventing miscalculations
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
  fun SpatialOffset.offsetIn(target: CoordinateSpace): Offset

  fun SpatialOffset.toSpace(target: CoordinateSpace): SpatialOffset {
    return SpatialOffset(
      offset = offsetIn(target),
      space = target,
    )
  }
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
