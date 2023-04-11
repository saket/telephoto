package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.times
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toOffset
import me.saket.telephoto.zoomable.internal.discardFractionalParts

// todo: should this be called DrawRegion?
//  no, canvas draw region can be smaller than images.
/**
 * For [Modifier.zoomable] to be able to correctly scale and pan its content, it uses
 * [ZoomableContentLocation] to understand the content's _visual_ size & position to prevent them
 * from going out of bounds.
 *
 * [Modifier.zoomable] can't calculate this on its own by inspecting its layout bounds because
 * that may be smaller or larger than the content's visual size. For instance, an `Image` composable
 * that uses `Modifier.fillMaxSize()` could actually be drawing an image that only fills half its
 * height. Another possibility is a sub-sampled composable such as a map whose full sized content
 * could be at an order of magnitude larger than its layout bounds.
 */
interface ZoomableContentLocation {
  companion object {
    /**
     * Describes a zoomable content's location that is positioned in the center of its layout
     * and is downscaled only if its size exceeds its layout bounds while maintaining its
     * original aspect ratio.
     *
     * That is, its alignment = [Alignment.Center] and scale = [ContentScale.Inside].
     */
    @Stable
    fun scaledInsideAndCenterAligned(size: Size?): ZoomableContentLocation {
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
    fun unscaledAndTopStartAligned(size: Size?): ZoomableContentLocation {
      return when {
        size == null || size.isUnspecified -> Unspecified
        else -> RelativeContentLocation(
          size = size,
          scale = ContentScale.None,
          alignment = Alignment.TopStart,
        )
      }
    }
  }

  // todo: doc
  object Unspecified : ZoomableContentLocation {
    override fun calculateBoundsInside(layoutSize: Size, direction: LayoutDirection) =
      throw UnsupportedOperationException()

    override fun toString(): String {
      return this::class.simpleName!!
    }
  }

  // todo: doc
  object SameAsLayoutBounds : ZoomableContentLocation {
    override fun calculateBoundsInside(layoutSize: Size, direction: LayoutDirection) =
      Rect(Offset.Zero, layoutSize)
  }

  // todo: think of a better name that makes it clear this isn't the layout bounds.
  fun calculateBoundsInside(layoutSize: Size, direction: LayoutDirection): Rect
}

internal val ZoomableContentLocation.isSpecified
  get() = this !is ZoomableContentLocation.Unspecified

/**
 * This isn't public because only a few combinations of [ContentScale]
 * and [Alignment] work perfectly for all kinds of content.
 */
@Immutable
internal data class RelativeContentLocation(
  val size: Size,
  val scale: ContentScale,
  val alignment: Alignment,
) : ZoomableContentLocation {
  override fun calculateBoundsInside(layoutSize: Size, direction: LayoutDirection): Rect {
    val scaleFactor = scale.computeScaleFactor(
      srcSize = size,
      dstSize = layoutSize,
    )
    val scaledSize = size.times(scaleFactor)
    val alignedOffset = alignment.align(
      size = scaledSize.discardFractionalParts(),
      space = layoutSize.discardFractionalParts(),
      layoutDirection = direction,
    )
    return Rect(
      offset = alignedOffset.toOffset(),
      size = scaledSize
    )
  }
}
