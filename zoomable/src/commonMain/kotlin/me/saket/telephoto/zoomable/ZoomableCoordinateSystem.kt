package me.saket.telephoto.zoomable

import androidx.compose.ui.geometry.Size
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.internal.ContentCoordinateSpace
import me.saket.telephoto.zoomable.internal.ViewportCoordinateSpace
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.spatial.CoordinateSystem
import me.saket.telephoto.zoomable.spatial.SpatialRect
import kotlin.jvm.JvmSynthetic

/**
 * `Modifier.zoomable()`'s coordinate system for representing spatial offsets in
 * [CoordinateSpace.Viewport][CoordinateSpace.Companion.Viewport] and
 * [CoordinateSpace.ZoomableContent][CoordinateSpace.Companion.ZoomableContent].
 *
 * Usage example:
 *
 * ```kotlin
 * val visibleImageRegion = with(zoomableState.coordinateSystem) {
 *   contentBounds.rectIn(CoordinateSpace.ZoomableContent)
 * }
 * ```
 */
@ExperimentalTelephotoApi
interface ZoomableCoordinateSystem : CoordinateSystem {
  /**
   * The visual bounds of the content _after_ user zoom and pan. This is calculated by applying
   * [contentScale][ZoomableState.contentScale] and [contentAlignment][ZoomableState.contentAlignment]
   * to the value passed to [ZoomableState.setContentLocation].
   *
   * This value will be [SpatialRect.Unspecified] if the content hasn't been measured yet, and it will
   * never exceed the viewport bounds.
   */
  @ExperimentalTelephotoApi
  val contentBounds: SpatialRect

  /**
   * Like [contentBounds], but _without_ any user transformations. This is the initial bounds of the
   * content, where the content is displayed prior to any zoom or pan gestures. This property is
   * intended for drawing decorations around the content that remain unaffected by zoom and pan gestures.
   */
  @ExperimentalTelephotoApi
  val unscaledContentBounds: SpatialRect

  /**
   * Size of the composable where `Modifier.zoomable()` is used.
   *
   * This value will be [Size.Zero] if the composable hasn't been measured yet.
   */
  val viewportSize: Size
}

/**
 * Represents the coordinate space of the visible viewport — the bounds of
 * the composable where `Modifier.zoomable()` is applied.
 *
 * Useful for interpreting user input or layout positions on screen.
 */
@ExperimentalTelephotoApi
val CoordinateSpace.Companion.Viewport: CoordinateSpace
  @JvmSynthetic get() = ViewportCoordinateSpace

/**
 * Represents the coordinate space of the zoomable content (e.g., an image).
 *
 * Offsets in this space are relative to the unscaled, unpanned content bounds.
 * Useful for anchoring elements to the original content or mapping coordinates
 * from click listeners (e.g., `onClick`, `onLongClick`) on the viewport to the content.
 */
@ExperimentalTelephotoApi
val CoordinateSpace.Companion.ZoomableContent: CoordinateSpace
  @JvmSynthetic get() = ContentCoordinateSpace
