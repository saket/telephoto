package me.saket.telephoto.zoomable.spatial

import me.saket.telephoto.ExperimentalTelephotoApi

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
