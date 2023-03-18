package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.times
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toOffset
import me.saket.telephoto.zoomable.internal.discardFractionalParts

/**
 * For [ZoomableViewport] to be able to correctly scale and pan its content, it uses
 * [ZoomableContentLocation] to understand the content's _visual_ size and position to prevent them
 * from going out of bounds.
 *
 * [ZoomableViewport] can't calculate this on its own by inspecting its children's layout bounds
 * because that may not always match the content's visual size. For instance, an `Image` composable
 * that uses `Modifier.fillMaxSize()` could actually be drawing an image that only fills half its
 * size. An another possibility is a sub-sampled composable such as a map whose full sized content
 * could be at an order of magnitude larger than the layout bounds.
 */
interface ZoomableContentLocation {
  companion object {
    /**
     * Describes a zoomable content's location that is positioned in the center of its layout
     * and is downscaled only if its size exceeds its layout bounds while maintaining its
     * original aspect ratio.
     *
     * That is, its alignment = [Alignment.Center] and scale = [ContentScale.Inside].
     *
     * For an `Image` composable, its image will always be centered, but its scale will
     * need to be changed from the default value of `Fit` to `Inside`.
     *
     * ```
     * Image(
     *   painter = …,
     *   contentScale = ContentScale.Inside
     * )
     * ```
     */
    @Stable
    fun fitInsideAndCenterAligned(size: Size?): ZoomableContentLocation {
      return when {
        size == null || size.isUnspecified -> Unspecified
        else -> RelativeContentLocation(
          size = size,
          scale = ContentScale.Inside,
          alignment = Alignment.Center,
        )
      }
    }

    @Stable
    val Unspecified = object : ZoomableContentLocation {
      override fun boundsIn(parent: Rect, direction: LayoutDirection) =
        error("location is unspecified")
    }
  }

  fun boundsIn(parent: Rect, direction: LayoutDirection): Rect
}

/**
 * It's intentional that is not a data class. Setting a new location object should
 * always trigger a position update even if the content size is unchanged because two
 * images can have the same size.
 * */
@Immutable
internal class RelativeContentLocation(
  val size: Size,
  val scale: ContentScale,
  val alignment: Alignment,
) : ZoomableContentLocation {
  override fun boundsIn(parent: Rect, direction: LayoutDirection): Rect {
    val scaleFactor = scale.computeScaleFactor(
      srcSize = size,
      dstSize = parent.size,
    )
    val scaledSize = size.times(scaleFactor)
    val alignedOffset = alignment.align(
      size = scaledSize.discardFractionalParts(),
      space = parent.size.discardFractionalParts(),
      layoutDirection = direction,
    )
    return Rect(
      offset = alignedOffset.toOffset(),
      size = scaledSize
    )
  }
}

internal val ZoomableContentLocation.isSpecified get() = this !== ZoomableContentLocation.Unspecified
