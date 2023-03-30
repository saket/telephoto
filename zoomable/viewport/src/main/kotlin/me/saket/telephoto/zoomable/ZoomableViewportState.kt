package me.saket.telephoto.zoomable

import androidx.annotation.FloatRange
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.Spring.StiffnessMediumLow
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.MutatePriority
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.util.lerp
import me.saket.telephoto.zoomable.GestureTransformation.Companion.ZeroScaleFactor
import me.saket.telephoto.zoomable.internal.TransformableState
import me.saket.telephoto.zoomable.internal.ZoomableViewportSavedState
import me.saket.telephoto.zoomable.internal.div
import me.saket.telephoto.zoomable.internal.maxScale
import me.saket.telephoto.zoomable.internal.relativeTo
import me.saket.telephoto.zoomable.internal.roundToIntSize
import me.saket.telephoto.zoomable.internal.times
import me.saket.telephoto.zoomable.internal.topLeftCoercedInside
import me.saket.telephoto.zoomable.internal.unaryMinus
import kotlin.math.abs

/** todo: doc */
@Composable
fun rememberZoomableViewportState(
  maxZoomFactor: Float = 2f,
): ZoomableViewportState {
  val state = rememberSaveable(saver = ZoomableViewportState.Saver) {
    ZoomableViewportState()
  }.also {
    it.zoomRange = ZoomRange(maxZoomAsRatioOfSize = maxZoomFactor)
    it.layoutDirection = LocalLayoutDirection.current
  }

  if (state.isReadyToInteract) {
    LaunchedEffect(
      state.viewportBounds,
      state.contentLayoutBounds,
      state.unscaledContentLocation,
      state.contentAlignment,
      state.contentScale,
      state.layoutDirection,
      state.gestureTransformation == null,
    ) {
      state.refreshContentPosition()
    }
  }
  return state
}

@Stable
class ZoomableViewportState internal constructor(
  initialTransformation: GestureTransformation? = null
) {

  /**
   * Transformations that should be applied to viewport's content.
   */
  // todo: doc
  val contentTransformation: ZoomableContentTransformation by derivedStateOf {
    gestureTransformation.let {
      ZoomableContentTransformation(
        viewportBounds = viewportBounds.relativeTo(contentLayoutBounds),
        scale = it?.zoom?.finalZoom() ?: ZeroScaleFactor,  // Hide content until an initial zoom value is calculated.
        offset = if (it != null) -it.offset * it.zoom else Offset.Zero,
        rotationZ = 0f,
      )
    }
  }

  /**
   * Single source of truth for your content's aspect ratio. If you're using `Modifier.zoomable()`
   * with `Image()` or other composables that also accept [ContentScale], they should be set to
   * [ContentScale.None] to avoid any conflicts.
   *
   * A visual guide of the various scale values can be found
   * [here](https://developer.android.com/jetpack/compose/graphics/images/customize#content-scale).
   */
  var contentScale by mutableStateOf<ContentScale>(ContentScale.None)

  // todo: doc
  //  explain how the alignment affects zooming.
  var contentAlignment by mutableStateOf(Alignment.Center)

  /**
   * The content's current zoom as a fraction of its min and max allowed zoom factors.
   *
   * @return A value between 0 and 1, where 0 indicates that the image is fully zoomed out,
   * 1 indicates that the image is fully zoomed in, and `null` indicates that an initial zoom
   * value hasn't been calculated yet. A `null` value could be safely treated the same as 0, but
   * [Modifier.zoomable] leaves that up to you.
   */
  @get:FloatRange(from = 0.0, to = 1.0)
  val zoomFraction: Float? by derivedStateOf {
    gestureTransformation?.let {
      val min = zoomRange.minZoom(it.zoom.baseZoom)
      val max = zoomRange.maxZoom(it.zoom.baseZoom)
      val current = it.zoom.finalZoom().maxScale
      ((current - min) / (max - min)).coerceIn(0f, 1f)
    }
  }

  // todo: is "gesture" transformation the right name?
  internal var gestureTransformation: GestureTransformation? by mutableStateOf(initialTransformation)

  // todo: explain why this isn't a state?
  //  counter-arg: making this a state will allow live edit to work.
  internal var zoomRange = ZoomRange.Default

  internal lateinit var layoutDirection: LayoutDirection

  /**
   * Raw size of the image/video/anything without any scaling applied.
   * Used only for ensuring that the content does not pan/zoom outside its limits.
   */
  // TODO: verify doc.
  internal var unscaledContentLocation by mutableStateOf(ZoomableContentLocation.Unspecified)

  /**
   * Bounds of [ZoomableViewport]'s content composable in the layout hierarchy, without any scaling applied.
   */
  internal var contentLayoutBounds by mutableStateOf(Rect.Zero)

  internal val viewportBounds: Rect get() = Rect(Offset.Zero, size = contentLayoutBounds.size)

  /** todo: doc. */
  internal val isReadyToInteract: Boolean by derivedStateOf {
    unscaledContentLocation.isSpecified
      && contentLayoutBounds != Rect.Zero
      && viewportBounds != Rect.Zero
  }

  @Suppress("NAME_SHADOWING")
  internal val transformableState = TransformableState(
    canConsumePanChange = { panDelta ->
      val current = gestureTransformation

      if (current != null) {
        val panDeltaWithZoom = panDelta / current.zoom
        val newOffset = (current.offset - panDeltaWithZoom)

        // todo: this piece of code is duplicated with onGesture.
        val newOffsetWithinBounds = run {
          val unscaledContentBounds = unscaledContentLocation.boundsIn(
            parent = contentLayoutBounds,
            direction = layoutDirection
          )
          val drawRegionOffset = contentLayoutBounds.topLeft + (unscaledContentBounds.topLeft * current.zoom)
          newOffset.withZoomAndTranslate(zoom = -current.zoom.finalZoom(), translate = drawRegionOffset) {
            val expectedDrawRegion = Rect(offset = it, size = unscaledContentBounds.size * current.zoom)
            expectedDrawRegion.topLeftCoercedInside(viewportBounds, contentAlignment, layoutDirection)
          }
        }

        val consumedPan = panDeltaWithZoom - (newOffsetWithinBounds - newOffset)
        val isHorizontalPan = abs(panDeltaWithZoom.x) > abs(panDeltaWithZoom.y)

        // Give up this gesture if the content is almost near its edges.
        // As a user, I've always hated it when I'm scrolling images in a
        // horizontal pager, and an image that is only a pixel away from its
        // edge is preventing the pager from scrolling. I might remove this in
        // the future if it turns out to be useless.
        val wasAnyPanChangeConsumed = (if (isHorizontalPan) abs(consumedPan.x) else abs(consumedPan.y)) >= 1f
        wasAnyPanChangeConsumed

      } else {
        // Content is probably not ready yet. Ignore this gesture.
        false
      }
    },
    onTransformation = { zoomDelta, panDelta, _, centroid ->
      val unscaledContentBounds = unscaledContentLocation.boundsIn(
        parent = contentLayoutBounds,
        direction = layoutDirection
      )

      // This is the minimum scale needed to position the
      // content within its viewport w.r.t. its content scale.
      val baseZoom = contentScale.computeScaleFactor(
        srcSize = unscaledContentBounds.size,
        dstSize = contentLayoutBounds.size,
      )

      val oldZoom = ContentZoom(
        baseZoom = baseZoom,
        viewportZoom = gestureTransformation?.zoom?.viewportZoom ?: 1f
      )

      val isZoomingOut = zoomDelta < 1f
      val isZoomingIn = zoomDelta > 1f

      // Apply elasticity if content is being over/under-zoomed.
      val isAtMaxZoom = oldZoom.isAtMaxZoom(zoomRange)
      val isAtMinZoom = oldZoom.isAtMinZoom(zoomRange)
      val zoomDelta = when {
        isZoomingIn && isAtMaxZoom -> 1f + zoomDelta / 250
        isZoomingOut && isAtMinZoom -> 1f - zoomDelta / 250
        else -> zoomDelta
      }
      val newZoom = ContentZoom(
        baseZoom = baseZoom,
        viewportZoom = oldZoom.viewportZoom * zoomDelta
      ).let {
        if (isAtMinZoom || isAtMaxZoom) {
          // Apply a hard-stop after a limit.
          it.coercedIn(
            range = zoomRange,
            leewayPercentForMinZoom = 0.1f,
            leewayPercentForMaxZoom = 0.4f
          )
        } else {
          it
        }
      }

      val oldOffset = gestureTransformation.let {
        if (it != null) {
          it.offset
        } else {
          val defaultAlignmentOffset = contentAlignment.align(
            size = (unscaledContentBounds.size * baseZoom).roundToIntSize(),
            space = (viewportBounds.size).roundToIntSize(),
            layoutDirection = layoutDirection
          )
          // Take the content's top-left into account because it may not start at 0,0.
          unscaledContentBounds.topLeft + (-defaultAlignmentOffset.toOffset() / oldZoom)
        }
      }

      // Copied from androidx samples:
      // https://github.com/androidx/androidx/blob/643b1cfdd7dfbc5ccce1ad951b6999df049678b3/compose/foundation/foundation/samples/src/main/java/androidx/compose/foundation/samples/TransformGestureSamples.kt#L87
      //
      // For natural zooming and rotating, the centroid of the gesture
      // should be the fixed point where zooming and rotating occurs.
      //
      // We compute where the centroid was (in the pre-transformed coordinate
      // space), and then compute where it will be after this delta.
      //
      // We then compute what the new offset should be to keep the centroid
      // visually stationary for rotating and zooming, and also apply the pan.
      //
      // This is comparable to performing a pre-translate + scale + post-translate on
      // a Matrix.
      //
      // I found this maths difficult to understand, so here's another explanation in
      // Ryan Harter's words:
      //
      // The basic idea is that to scale around an arbitrary point, you translate so that
      // that point is in the center, then you rotate, then scale, then move everything back.
      //
      // Note to self: these values are divided by zoom because that's how the final offset
      // for UI is calculated: -offset * zoom.
      //
      //              Move the centroid to the center
      //                  of panned content(?)
      //                           |                            Scale
      //                           |                              |                Move back
      //                           |                              |           (+ new translation)
      //                           |                              |                    |
      //              _____________|_________________     ________|_________   ________|_________
      val newOffset = (oldOffset + centroid / oldZoom) - (centroid / newZoom + panDelta / oldZoom)

      gestureTransformation = GestureTransformation(
        offset = run {
          // To ensure that the content always stays within the viewport, the content's actual draw
          // region will need to be calculated. This is important because the content's draw region may
          // or may not be equal to its full size. For e.g., a 16:9 image displayed in a 1:2 viewport
          // will have a lot of empty space on both vertical sides.
          val drawRegionOffset = contentLayoutBounds.topLeft + (unscaledContentBounds.topLeft * newZoom)

          // Note to self: (-offset * zoom) is the final value used for displaying the content composable.
          newOffset.withZoomAndTranslate(zoom = -newZoom.finalZoom(), translate = drawRegionOffset) {
            val expectedDrawRegion = Rect(offset = it, size = unscaledContentBounds.size * newZoom)
            expectedDrawRegion.topLeftCoercedInside(viewportBounds, contentAlignment, layoutDirection)
          }
        },
        zoom = newZoom,
        lastCentroid = centroid,
      )
    }
  )

  private operator fun Offset.div(zoom: ContentZoom): Offset = div(zoom.finalZoom())
  private operator fun Offset.times(zoom: ContentZoom): Offset = times(zoom.finalZoom())
  private operator fun Size.times(zoom: ContentZoom): Size = times(zoom.finalZoom())

  // todo: doc
  /** Update content position by using its current zoom and offset values. */
  internal suspend fun refreshContentPosition() {
    check(isReadyToInteract)
    transformableState.transform(MutatePriority.PreventUserInput) {
      transformBy(/* default values */)
    }
  }

  /** todo: doc */
  fun setContentLocation(location: ZoomableContentLocation) {
    unscaledContentLocation = location
  }

  /** Reset content to its minimum zoom and zero offset values **without** any animation. */
  fun resetZoomAndPanImmediately() {
    gestureTransformation = null
  }

  suspend fun zoomOut() {
    smoothlyToggleZoom(
      shouldZoomIn = false,
      centroidInViewport = Offset.Zero,
    )
  }

  internal suspend fun handleDoubleTapZoomTo(centroidInViewport: Offset) {
    val start = gestureTransformation ?: return
    smoothlyToggleZoom(
      shouldZoomIn = !start.zoom.isAtMaxZoom(zoomRange),
      centroidInViewport = centroidInViewport
    )
  }

  private suspend fun smoothlyToggleZoom(
    shouldZoomIn: Boolean,
    centroidInViewport: Offset
  ) {
    val start = gestureTransformation ?: return

    val targetZoomFactor = if (shouldZoomIn) {
      zoomRange.maxZoom(baseZoom = start.zoom.baseZoom)
    } else {
      zoomRange.minZoom(baseZoom = start.zoom.baseZoom)
    }
    val targetZoom = start.zoom.copy(
      viewportZoom = targetZoomFactor / (start.zoom.baseZoom.maxScale)
    )

    // todo: this piece of code is duplicated with onGesture.
    val targetOffset = run {
      val proposedOffset = (start.offset + centroidInViewport / start.zoom) - (centroidInViewport / targetZoom)
      val unscaledContentBounds = unscaledContentLocation.boundsIn(
        parent = contentLayoutBounds,
        direction = layoutDirection
      )

      val drawRegionOffset = contentLayoutBounds.topLeft + (unscaledContentBounds.topLeft * targetZoom)

      // Note to self: (-offset * zoom) is the final value used for displaying the content composable.
      proposedOffset.withZoomAndTranslate(zoom = -targetZoom.finalZoom(), translate = drawRegionOffset) {
        val expectedDrawRegion = Rect(offset = it, size = unscaledContentBounds.size * targetZoom)
        expectedDrawRegion.topLeftCoercedInside(viewportBounds, contentAlignment, layoutDirection)
      }
    }

    transformableState.transform(MutatePriority.UserInput) {
      AnimationState(initialValue = 0f).animateTo(
        targetValue = 1f,
        // Without a low visibility threshold, spring() makes a huge
        // jump on its last frame causing a few frames to be dropped.
        animationSpec = spring(stiffness = StiffnessMediumLow, visibilityThreshold = 0.0001f)
      ) {
        val animatedZoom = start.zoom.copy(
          viewportZoom = lerp(
            start = start.zoom.viewportZoom,
            stop = targetZoom.viewportZoom,
            fraction = value
          )
        )
        // For animating the offset, it is necessary to interpolate between values that the UI
        // will see (i.e., -offset * zoom). Otherwise, a curve animation is produced if only the
        // offset is used because the zoom and the offset values animate at different scales.
        val animatedOffsetForUi = lerp(
          start = (-start.offset * start.zoom),
          stop = (-targetOffset * targetZoom),
          fraction = value
        )

        gestureTransformation = GestureTransformation(
          offset = (-animatedOffsetForUi) / animatedZoom,
          zoom = animatedZoom,
          lastCentroid = centroidInViewport,
        )
      }
    }
  }

  internal fun isZoomOutsideRange(): Boolean {
    val currentZoom = gestureTransformation!!.zoom
    val viewportZoomWithinBounds = currentZoom.coercedIn(zoomRange).viewportZoom
    return currentZoom.viewportZoom != viewportZoomWithinBounds
  }

  internal suspend fun smoothlySettleZoomOnGestureEnd() {
    val start = gestureTransformation!!
    val viewportZoomWithinBounds = start.zoom.coercedIn(zoomRange).viewportZoom

    transformableState.transform {
      var previous = start.zoom.viewportZoom
      AnimationState(initialValue = previous).animateTo(
        targetValue = viewportZoomWithinBounds,
        animationSpec = spring()
      ) {
        transformBy(
          centroid = start.lastCentroid,
          zoomChange = if (previous == 0f) 1f else value / previous,
        )
        previous = this.value
      }
    }
  }

  internal suspend fun fling(velocity: Velocity, density: Density) {
    val start = gestureTransformation!!
    transformableState.transform {
      var previous = start.offset
      AnimationState(
        typeConverter = Offset.VectorConverter,
        initialValue = previous,
        initialVelocityVector = AnimationVector(velocity.x, velocity.y)
      ).animateDecay(splineBasedDecay(density)) {
        transformBy(
          centroid = start.lastCentroid,
          panChange = value - previous
        )
        previous = value
      }
    }
  }

  companion object {
    internal val Saver = Saver<ZoomableViewportState, ZoomableViewportSavedState>(
      save = { ZoomableViewportSavedState(it.gestureTransformation) },
      restore = { ZoomableViewportState(initialTransformation = it.gestureTransformation()) }
    )
  }
}

// todo: doc
internal data class GestureTransformation(
  val offset: Offset,
  val zoom: ContentZoom,
  val lastCentroid: Offset,
) {
  companion object {
    val ZeroScaleFactor = ScaleFactor(0f, 0f)
  }
}

// todo: doc
internal data class ContentZoom(
  val baseZoom: ScaleFactor,
  val viewportZoom: Float,
) {
  // todo: doc
  fun finalZoom(): ScaleFactor {
    return baseZoom * viewportZoom
  }

  fun coercedIn(
    range: ZoomRange,
    leewayPercentForMinZoom: Float = 0f,
    leewayPercentForMaxZoom: Float = leewayPercentForMinZoom,
  ): ContentZoom {
    val minViewportZoom = range.minZoom(baseZoom) / baseZoom.maxScale
    val maxViewportZoom = range.maxZoom(baseZoom) / baseZoom.maxScale
    return copy(
      baseZoom = baseZoom,
      viewportZoom = viewportZoom.coerceIn(
        minimumValue = minViewportZoom * (1 - leewayPercentForMinZoom),
        maximumValue = maxViewportZoom * (1 + leewayPercentForMaxZoom),
      )
    )
  }

  fun isAtMinZoom(range: ZoomRange): Boolean {
    return finalZoom().maxScale <= range.minZoom(baseZoom = baseZoom)
  }

  fun isAtMaxZoom(range: ZoomRange): Boolean {
    return finalZoom().maxScale >= range.maxZoom(baseZoom = baseZoom)
  }
}

// todo: doc.
internal data class ZoomRange(
  private val minZoomAsRatioOfBaseZoom: Float = 1f,
  private val maxZoomAsRatioOfSize: Float,
) {

  // todo: ZoomRange and ContentZoom are inter-dependent. minZoom() and maxZoom() should probably move to ContentZoom.
  internal fun minZoom(baseZoom: ScaleFactor): Float {
    return minZoomAsRatioOfBaseZoom * baseZoom.maxScale
  }

  internal fun maxZoom(baseZoom: ScaleFactor): Float {
    // Note to self: the max zoom factor can be less than the min zoom
    // factor if the content is scaled-up by default. This can be tested
    // by setting contentScale = CenterCrop.
    return maxOf(maxZoomAsRatioOfSize, minZoom(baseZoom))
  }

  companion object {
    val Default = ZoomRange(minZoomAsRatioOfBaseZoom = 1f, maxZoomAsRatioOfSize = 1f)
  }
}

// todo: improve doc.
/** This is named along the lines of `Canvas#withTranslate()`. */
private fun Offset.withZoomAndTranslate(zoom: ScaleFactor, translate: Offset, action: (Offset) -> Offset): Offset {
  return (action((this * zoom) + translate) - translate) / zoom
}
