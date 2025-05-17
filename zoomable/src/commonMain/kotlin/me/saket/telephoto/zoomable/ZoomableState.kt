@file:Suppress("DeprecatedCallableAddReplaceWith")

package me.saket.telephoto.zoomable

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.spatial.SpatialOffset
import kotlin.jvm.JvmName

/**
 * Create a [ZoomableState] that can be used with [Modifier.zoomable].
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
  zoomSpec: ZoomSpec = ZoomSpec(maxZoomFactor = 2f),
  autoApplyTransformations: Boolean = true,
  hardwareShortcutsSpec: HardwareShortcutsSpec = HardwareShortcutsSpec(),
): ZoomableState {
  return rememberSaveable(saver = RealZoomableState.Saver) {
    RealZoomableState(
      savedState = null,
      autoApplyTransformations = autoApplyTransformations,
    )
  }.also {
    it.zoomSpec = zoomSpec
    it.hardwareShortcutsSpec = hardwareShortcutsSpec
    it.layoutDirection = LocalLayoutDirection.current
    it.density = LocalDensity.current
    //it.RetainPanAcrossContentSizeChangesEffect()
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
   * Single source of truth for your content's aspect ratio. Defaults to [ContentScale.Fit].
   *
   * If you're using `Modifier.zoomable()` with `Image()` or other composables that also accept
   * [ContentScale], they should not be used to avoid any conflicts.
   *
   * A visual guide of the various scale values can be found
   * [here](https://developer.android.com/jetpack/compose/graphics/images/customize#content-scale).
   */
  var contentScale: ContentScale

  /**
   * Alignment of the content. Defaults to [Alignment.Center].
   *
   * When the content is zoomed, it is scaled with respect to this alignment until it
   * is large enough to fill all available space. After that, they're scaled uniformly.
   * */
  var contentAlignment: Alignment

  /**
   * Padding around the zoomable content _within_ the viewport. This will add padding for the.
   * content after it has been clipped, which is not possible via [Modifier.padding].
   */
  var contentPadding: PaddingValues

  /**
   * The visual bounds of the content _with_ user zoom and pan. This is calculated by applying
   * [contentScale] and [contentAlignment] to the value passed to [ZoomableState.setContentLocation].
   * This property is intended for drawing decorations around the content or for performing hit tests.
   *
   * This value will be [Rect.Zero] if the content hasn't been measured yet, and it will never
   * exceed the viewport bounds.
   */
  @Deprecated(
    message = "Superseded by coordinateSystem.contentBounds.",
    replaceWith = ReplaceWith("coordinateSystem.contentBounds"),
  )
  val transformedContentBounds: Rect

  /**
   * The content's current zoom as a fraction of its min and max allowed zoom limits.
   * Also see: [ZoomableContentTransformation.scale] and [ZoomableContentTransformation.scaleMetadata].
   *
   * @return A value between 0 and 1, where 0 indicates that the content is fully zoomed out,
   * 1 indicates that the content is fully zoomed in, and `null` indicates that an initial zoom
   * value hasn't been calculated yet and the content is hidden. A `null` value could be safely
   * treated the same as 0, but [Modifier.zoomable] leaves that decision up to you.
   */
  val zoomFraction: Float?

  /** The zoom spec passed to [rememberZoomableState]. */
  val zoomSpec: ZoomSpec

  /** Whether any zoom, pan (or both) animation is in progress. */
  val isAnimationRunning: Boolean

  // todo: add some basic tests
  /**
   * `Modifier.zoomable()`'s coordinate system for representing spatial offsets in
   * [CoordinateSpace.Viewport][CoordinateSpace.Companion.Viewport] and
   * [CoordinateSpace.ZoomableContent][CoordinateSpace.Companion.ZoomableContent].
   *
   * Usage example:
   *
   * ```kotlin
   * val state = rememberZoomableState()
   *
   * val viewportCenter = SpatialOffset(
   *   offset = Offset(100f, 100f),
   *   space = CoordinateSpace.Viewport,
   * )
   *
   * // If a 200 x 200 viewport is showing a zoomed-out 500 x 500 image,
   * // this will return (250f, 250f) in the image's coordinate space.
   * val imageCenter: Offset = with(state.coordinateSystem) {
   *   viewportCenter.offsetIn(CoordinateSpace.ZoomableContent)
   * }
   * ```
   */
  @ExperimentalTelephotoApi
  val coordinateSystem: ZoomableCoordinateSystem

  /** See [ZoomableContentLocation]. */
  fun setContentLocation(location: ZoomableContentLocation)

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
  @OptIn(ExperimentalTelephotoApi::class)
  suspend fun zoomBy(
    zoomFactor: Float,
    centroid: Offset = Offset.Unspecified,
    animationSpec: AnimationSpec<Float> = DefaultZoomAnimationSpec,
  ) {
    zoomBy(
      zoomFactor = zoomFactor,
      centroid = SpatialOffset(centroid, CoordinateSpace.Viewport),
      animationSpec = animationSpec,
    )
  }

  /** See [zoomBy]. */
  @ExperimentalTelephotoApi
  suspend fun zoomBy(
    zoomFactor: Float,
    centroid: SpatialOffset,
    animationSpec: AnimationSpec<Float> = DefaultZoomAnimationSpec,
  )

  /**
   * Zooms in or out around [centroid] to achieve a final zoom level specified by [zoomFactor],
   * and suspends until it's finished.
   *
   * @param zoomFactor Target zoom level for the content. For example, a [zoomFactor] of `2f` will
   * set the content's zoom level to two times its *original* size. This value is internally coerced
   * between [ZoomSpec.maximum] and [ZoomSpec.minimum].
   *
   * @param centroid Focal point for this zoom within the content's size. Defaults to the center
   * of the viewport.
   *
   * @param animationSpec The animation spec to use or [SnapSpec] for no animation.
   */
  @OptIn(ExperimentalTelephotoApi::class)
  suspend fun zoomTo(
    zoomFactor: Float,
    centroid: Offset = Offset.Unspecified,
    animationSpec: AnimationSpec<Float> = DefaultZoomAnimationSpec,
  ) {
    zoomTo(
      zoomFactor = zoomFactor,
      centroid = SpatialOffset(centroid, CoordinateSpace.Viewport),
      animationSpec = animationSpec,
    )
  }

  /** See [zoomTo]. */
  @ExperimentalTelephotoApi
  suspend fun zoomTo(
    zoomFactor: Float,
    centroid: SpatialOffset,
    animationSpec: AnimationSpec<Float> = DefaultZoomAnimationSpec,
  )

  /**
   * Animate pan by [offset] in pixels and suspend until it's finished.
   *
   * @param animationSpec The animation spec to use or [SnapSpec] for no animation.
   */
  @OptIn(ExperimentalTelephotoApi::class)
  suspend fun panBy(
    offset: Offset,
    animationSpec: AnimationSpec<Offset> = DefaultPanAnimationSpec,
  ) {
    panBy(
      offset = SpatialOffset(offset, CoordinateSpace.Viewport),
      animationSpec = animationSpec,
    )
  }

  /** See [panBy]. */
  @ExperimentalTelephotoApi
  suspend fun panBy(
    offset: SpatialOffset,
    animationSpec: AnimationSpec<Offset> = DefaultPanAnimationSpec,
  )

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

  /** See [ZoomableContentLocation]. */
  @Deprecated(
    message = "Use setContentLocation() instead",
    replaceWith = ReplaceWith("setContentLocation"),
    level = DeprecationLevel.HIDDEN,
  )
  @Suppress("INAPPLICABLE_JVM_NAME", "unused")  // https://youtrack.jetbrains.com/issue/KT-31420
  @JvmName("setContentLocation")
  suspend fun setContentLocationSuspending(location: ZoomableContentLocation) {
    setContentLocation(location)
  }

  companion object {
    val DefaultZoomAnimationSpec: AnimationSpec<Float> get() = spring(stiffness = Spring.StiffnessMediumLow)
    val DefaultPanAnimationSpec: AnimationSpec<Offset> get() = spring(stiffness = Spring.StiffnessMediumLow)
    val DefaultSettleAnimationSpec: AnimationSpec<Float> get() = spring()
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
