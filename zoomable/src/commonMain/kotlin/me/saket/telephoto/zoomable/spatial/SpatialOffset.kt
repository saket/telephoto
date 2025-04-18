package me.saket.telephoto.zoomable.spatial

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

/** `false` when this is [SpatialOffset.Unspecified]. */
@Stable
@ExperimentalTelephotoApi
val SpatialOffset.isSpecified: Boolean
  get() = offset.isSpecified

/** `true` when this is [SpatialOffset.Unspecified]. */
@Stable
@ExperimentalTelephotoApi
val SpatialOffset.isUnspecified: Boolean
  get() = offset.isUnspecified

/**
 * If this [Offset] [isSpecified] then this is returned, otherwise [block] is executed
 * and its result is returned.
 */
@ExperimentalTelephotoApi
inline fun SpatialOffset.takeOrElse(block: () -> SpatialOffset): SpatialOffset =
  if (isSpecified) this else block()
