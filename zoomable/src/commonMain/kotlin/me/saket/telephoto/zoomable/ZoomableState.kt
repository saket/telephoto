@file:Suppress("DeprecatedCallableAddReplaceWith")

package me.saket.telephoto.zoomable

import androidx.annotation.FloatRange
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * Create a [ZoomableState] that can be used with [Modifier.zoomable].
 *
 * @param zoomSpec See [ZoomSpec.maxZoomFactor] and [ZoomSpec.preventOverOrUnderZoom].
 *
 * @param autoApplyTransformations Determines whether the resulting scale and translation of pan and zoom
 * gestures should be automatically applied by [Modifier.zoomable] to its content. This can be disabled
 * if your content prefers applying the transformations in a bespoke manner.
 *
 * @param hardwareShortcutsSpec Spec used for handling keyboard and mouse shortcuts, or
 * [HardwareShortcutsSpec.Disabled] for disabling them.
 */
@Composable
fun rememberZoomableState(
  zoomSpec: ZoomSpec = ZoomSpec(),
  autoApplyTransformations: Boolean = true,
  hardwareShortcutsSpec: HardwareShortcutsSpec = HardwareShortcutsSpec(),
): ZoomableState {
  return rememberSaveable(saver = RealZoomableState.Saver) {
    RealZoomableState(
      autoApplyTransformations = autoApplyTransformations,
    )
  }.also {
    it.zoomSpec = zoomSpec
    it.hardwareShortcutsSpec = hardwareShortcutsSpec
    it.layoutDirection = LocalLayoutDirection.current
  }
}

@Stable
sealed interface ZoomableState {
  /**
   * Transformations that should be applied to [Modifier.zoomable]'s content.
   *
   * See [ZoomableContentTransformation].
   */
  val contentTransformation: ZoomableContentTransformation

  /**
   * Determines whether the resulting scale and translation of pan and zoom gestures
   * should be automatically applied to by [Modifier.zoomable] to its content. This can
   * be disabled if your content prefers applying the transformations in a bespoke manner.
   * */
  var autoApplyTransformations: Boolean

  /**
   * Single source of truth for your content's aspect ratio. If you're using `Modifier.zoomable()`
   * with `Image()` or other composables that also accept [ContentScale], they should not be used
   * to avoid any conflicts.
   *
   * A visual guide of the various scale values can be found
   * [here](https://developer.android.com/jetpack/compose/graphics/images/customize#content-scale).
   */
  var contentScale: ContentScale

  /**
   * Alignment of the content.
   *
   * When the content is zoomed, it is scaled with respect to this alignment until it
   * is large enough to fill all available space. After that, they're scaled uniformly.
   * */
  var contentAlignment: Alignment

  /**
   * The visual bounds of the content, calculated by applying the scale and translation of pan and zoom
   * gestures to the value given to [ZoomableState.setContentLocation]. Useful for drawing decorations
   * around the content or performing hit tests.
   */
  val transformedContentBounds: Rect

  /**
   * The content's current zoom as a fraction of its min and max allowed zoom factors.
   *
   * @return A value between 0 and 1, where 0 indicates that the content is fully zoomed out,
   * 1 indicates that the content is fully zoomed in, and `null` indicates that an initial zoom
   * value hasn't been calculated yet and the content is hidden. A `null` value could be safely
   * treated the same as 0, but [Modifier.zoomable] leaves that decision up to you.
   */
  @get:FloatRange(from = 0.0, to = 1.0)
  val zoomFraction: Float?

  /** The zoom spec passed to [rememberZoomableState]. */
  val zoomSpec: ZoomSpec

  /** See [ZoomableContentLocation]. */
  fun setContentLocation(location: ZoomableContentLocation)

  /**
   * Reset content to its minimum zoom and zero offset and suspend until it's finished.
   */
  @Deprecated(message = "Use resetZoom(AnimationSpec) instead")
  suspend fun resetZoom(withAnimation: Boolean) {
    if (withAnimation) {
      resetZoom()
    } else {
      resetZoom(animationSpec = SnapSpec())
    }
  }

  /**
   * Reset content to its minimum zoom and zero offset and suspend until it's finished.
   *
   * @param animationSpec The animation spec to use or [SnapSpec] for no animation.
   */
  suspend fun resetZoom(animationSpec: AnimationSpec<Float> = DefaultZoomAnimationSpec)

  /**
   * Zooms in or out around [centroid] by a ratio of [zoomFactor] relative to the current size,
   * and suspends until it's finished.
   *
   * @param zoomFactor Ratio by which to zoom relative to the current size. For example, a [zoomFactor]
   * of `3f` will triple the *current* zoom level.
   *
   * @param centroid Focal point for this zoom within the content's size. Defaults to the center
   * of the content.
   *
   * @param animationSpec The animation spec to use or [SnapSpec] for no animation.
   */
  suspend fun zoomBy(
    zoomFactor: Float,
    centroid: Offset = Offset.Unspecified,
    animationSpec: AnimationSpec<Float> = DefaultZoomAnimationSpec,
  )

  /**
   * Zooms in or out around [centroid] to achieve a final zoom level specified by [zoomFactor],
   * and suspends until it's finished.
   *
   * @param zoomFactor Target zoom level for the content. For example, a [zoomFactor] of `2f` will
   * set the content's zoom level to two times its *original* size. This value is internally coerced
   * to at most [ZoomSpec.maxZoomFactor].
   *
   * @param centroid Focal point for this zoom within the content's size. Defaults to the center
   * of the content.
   *
   * @param animationSpec The animation spec to use or [SnapSpec] for no animation.
   */
  suspend fun zoomTo(
    zoomFactor: Float,
    centroid: Offset = Offset.Unspecified,
    animationSpec: AnimationSpec<Float> = DefaultZoomAnimationSpec,
  )

  /**
   * Animate pan by [offset] Offset in pixels and suspend until it's finished.
   *
   * @param animationSpec The animation spec to use or [SnapSpec] for no animation.
   */
  suspend fun panBy(
    offset: Offset,
    animationSpec: AnimationSpec<Offset> = DefaultPanAnimationSpec,
  )

  companion object {
    val DefaultZoomAnimationSpec: AnimationSpec<Float> get() = spring(stiffness = Spring.StiffnessMediumLow)
    val DefaultPanAnimationSpec: AnimationSpec<Offset> get() = spring(stiffness = Spring.StiffnessMediumLow)
  }
}

@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
@Composable
fun rememberZoomableState(
  zoomSpec: ZoomSpec = ZoomSpec(),
  autoApplyTransformations: Boolean = true,
): ZoomableState = rememberZoomableState(
  zoomSpec = zoomSpec,
  autoApplyTransformations = autoApplyTransformations,
  hardwareShortcutsSpec = HardwareShortcutsSpec(),
)
