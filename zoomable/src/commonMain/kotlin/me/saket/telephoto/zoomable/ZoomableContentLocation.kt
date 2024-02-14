package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.AbsoluteAlignment
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

/**
 * [Modifier.zoomable] uses [ZoomableContentLocation] to understand the content's _visual_ size and
 * position in order to prevent it from going out of bounds during pan & zoom gestures.
 *
 * The default value is [ZoomableContentLocation.SameAsLayoutBounds]. This is good enough for composables
 * that fill every pixel of their drawing space.
 *
 * For richer content such as an `Image()` composable whose visual size may not always match its layout
 * size, [ZoomableContentLocation] is used to indicate the visual region where pixels are actually drawn.
 *
 * If these words are proving confusing, refer to this
 * [video comparison](https://saket.github.io/telephoto/zoomable/#edge-detection) of zoomable content with
 * versus without [ZoomableContentLocation].
 */
interface ZoomableContentLocation {
  companion object {
    /**
     * Describes a zoomable content's location that is positioned in the center of its layout
     * and is downscaled only if its size exceeds its layout bounds while maintaining its
     * original aspect ratio.
     *
     * That is, its alignment is [Alignment.Center] and scale is [ContentScale.Inside].
     */
    @Stable
    fun scaledInsideAndCenterAligned(size: Size?): ZoomableContentLocation {
      return when {
        size == null -> Unspecified
        size.isUnspecified -> SameAsLayoutBounds
        else -> RelativeContentLocation(
          size = size,
          scale = ContentScale.Inside,
          alignment = Alignment.Center,
        )
      }
    }

    /**
     * Describes a zoomable content's location that is positioned in the center of its layout
     * and is already scaled to fit the layout bounds while maintaining its original aspect ratio.
     *
     * That is, its alignment is [Alignment.Center] and scale is [ContentScale.Fit].
     *
     * In most cases [ZoomableContentLocation.scaledInsideAndCenterAligned] should be preferred over
     * this because telephoto works best when [ZoomableState.contentScale] is the source of truth of
     * the content's scale.
     */
    @Stable
    fun scaledToFitAndCenterAligned(size: Size?): ZoomableContentLocation {
      return when {
        size == null -> Unspecified
        size.isUnspecified -> SameAsLayoutBounds
        else -> RelativeContentLocation(
          size = size,
          scale = ContentScale.Fit,
          alignment = Alignment.Center,
        )
      }
    }

    /**
     * Describes a zoomable content's location that is positioned at 0,0 of its layout
     * and is never scaled.
     *
     * That is, its alignment is [AbsoluteAlignment.TopLeft] and scale is [ContentScale.None].
     */
    @Stable
    fun unscaledAndTopLeftAligned(size: Size?): ZoomableContentLocation {
      return when {
        size == null -> Unspecified
        size.isUnspecified -> SameAsLayoutBounds
        else -> RelativeContentLocation(
          size = size,
          scale = ContentScale.None,
          alignment = AbsoluteAlignment.TopLeft,
        )
      }
    }

    @Deprecated(
      message = "Use unscaledAndTopLeftAligned() instead",
      replaceWith = ReplaceWith("ZoomableContentLocation.unscaledAndTopLeftAligned(size)"),
      level = DeprecationLevel.ERROR,
    )
    fun unscaledAndTopStartAligned(size: Size?) = unscaledAndTopLeftAligned(size)
  }

  /**
   * A placeholder value for indicating that the zoomable content's location
   * isn't calculated yet. The content will stay hidden until this is replaced.
   */
  object Unspecified : ZoomableContentLocation {
    override fun size(layoutSize: Size) = Size.Unspecified
    override fun location(layoutSize: Size, direction: LayoutDirection) = throw UnsupportedOperationException()
    override fun toString(): String = "ZoomableContentLocation.Unspecified"
  }

  /**
   * The default value of [ZoomableContentLocation], intended to be used for content that
   * fills every pixel of its layout size.
   *
   * For richer content such as images whose visual size may not always match its layout
   * size, you should provide a different value using [ZoomableState.setContentLocation].
   */
  object SameAsLayoutBounds : ZoomableContentLocation {
    override fun size(layoutSize: Size): Size = layoutSize
    override fun location(layoutSize: Size, direction: LayoutDirection) = Rect(Offset.Zero, layoutSize)
    override fun toString(): String = "ZoomableContentLocation.SameAsLayoutBounds"
  }

  fun size(layoutSize: Size): Size

  fun location(layoutSize: Size, direction: LayoutDirection): Rect
}

/**
 * This isn't public because only a few combinations of [ContentScale]
 * and [Alignment] work perfectly for all kinds of content.
 */
@Immutable
internal data class RelativeContentLocation(
  private val size: Size,
  private val scale: ContentScale,
  private val alignment: Alignment,
) : ZoomableContentLocation {
  override fun size(layoutSize: Size): Size = size

  override fun location(layoutSize: Size, direction: LayoutDirection): Rect {
    check(!layoutSize.isEmpty()) { "Layout size is empty" }

    val scaleFactor = scale.computeScaleFactor(
      srcSize = size,
      dstSize = layoutSize,
    )
    val scaledSize = size * scaleFactor
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
