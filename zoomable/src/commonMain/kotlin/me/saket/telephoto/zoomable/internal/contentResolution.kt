package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.LayoutDirection
import me.saket.telephoto.zoomable.ZoomableContentLocation
import kotlin.math.abs
import kotlin.math.max

/**
 * Detects the same content resolving at a different resolution, e.g. a preview being
 * replaced by its full quality version, and returns the scale from [previousSize] to it.
 * Returns null for anything else.
 *
 * [location] is laid out in [layoutSize], the layout [previousSize] was measured in, so
 * that a re-layout of the same content (e.g. [SameAsLayoutBounds][ZoomableContentLocation.SameAsLayoutBounds],
 * or an image scaled with ContentScale.Inside after a rotation) is not mistaken for a new resolution.
 */
internal fun contentResolutionChange(
  previousSize: Size,
  location: ZoomableContentLocation,
  layoutSize: Size,
  layoutDirection: LayoutDirection,
): ScaleFactor? {
  if (!previousSize.isSpecifiedAndNonEmpty || !layoutSize.isSpecifiedAndNonEmpty) {
    return null
  }
  val newSize = location.location(layoutSize, layoutDirection).size
  if (!newSize.isSpecifiedAndNonEmpty || newSize == previousSize) {
    return null
  }

  val scaleX = newSize.width / previousSize.width
  val scaleY = newSize.height / previousSize.height
  // Previews are rounded to whole pixels, so the aspect ratios of a small preview and its
  // full image can differ noticeably. Allow one pixel of rounding at the smaller size.
  val isSameAspectRatio =
    abs(previousSize.width * scaleY - newSize.width) <= max(1f, scaleY) &&
      abs(previousSize.height * scaleX - newSize.height) <= max(1f, scaleX)
  return if (isSameAspectRatio) ScaleFactor(scaleX, scaleY) else null
}
