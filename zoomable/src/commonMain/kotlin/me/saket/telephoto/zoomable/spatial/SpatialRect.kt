package me.saket.telephoto.zoomable.spatial

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect
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
 * val boundsInViewport = SpatialRect(
 *   rect = Rect(Offset.Zero, Size(200f, 300f)),
 *   space = CoordinateSpace.Viewport,
 * )
 *
 * val boundsInImage: Rect = with(zoomableState.coordinateSystem) {
 *   boundsInViewport.rectIn(CoordinateSpace.ZoomableContent)
 * }
 * ```
 */
@Poko
@Immutable
@ExperimentalTelephotoApi
class SpatialRect private constructor(
  val topLeft: SpatialOffset,
  val bottomRight: SpatialOffset,
) {

  constructor(
    rect: Rect,
    space: CoordinateSpace,
  ) : this(
    SpatialOffset(rect.topLeft, space),
    SpatialOffset(rect.bottomRight, space),
  )

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
