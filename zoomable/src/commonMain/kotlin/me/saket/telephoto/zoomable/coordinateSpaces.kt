package me.saket.telephoto.zoomable

import me.saket.telephoto.zoomable.internal.ContentCoordinateSpace
import me.saket.telephoto.zoomable.internal.ViewportCoordinateSpace

/**
 * Represents the coordinate space of the visible viewport — the bounds of
 * the composable where `Modifier.zoomable()` is applied.
 *
 * Useful for interpreting user input or layout positions on screen.
 */
@ExperimentalTelephotoApi
val CoordinateSpace.Companion.Viewport: CoordinateSpace
  get() = ViewportCoordinateSpace

/**
 * Represents the coordinate space of the zoomable content (e.g., an image).
 *
 * Offsets in this space are relative to the unscaled, unpanned content bounds.
 * Useful for anchoring elements to the original content or mapping coordinates
 * from click listeners (e.g., `onClick`, `onLongClick`) on the viewport to the content.
 */
@ExperimentalTelephotoApi
val CoordinateSpace.Companion.ZoomableContent: CoordinateSpace
  get() = ContentCoordinateSpace
