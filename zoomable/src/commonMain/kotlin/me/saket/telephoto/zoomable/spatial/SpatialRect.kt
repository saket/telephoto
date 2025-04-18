package me.saket.telephoto.zoomable.spatial

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.ExperimentalTelephotoApi

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

/** `false` when this is [SpatialRect.Unspecified]. */
@Stable
@ExperimentalTelephotoApi
val SpatialRect.isSpecified: Boolean
  get() = topLeft.isSpecified && bottomRight.isSpecified

/** `true` when this is [SpatialRect.Unspecified]. */
@Stable
@ExperimentalTelephotoApi
val SpatialRect.isUnspecified: Boolean
  get() = topLeft.isUnspecified || bottomRight.isUnspecified
