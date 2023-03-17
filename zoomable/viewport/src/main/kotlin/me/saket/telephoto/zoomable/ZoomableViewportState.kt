package me.saket.telephoto.zoomable

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import me.saket.telephoto.zoomable.internal.roundToIntSize
import me.saket.telephoto.zoomable.internal.times
import me.saket.telephoto.zoomable.internal.topLeftCoercedInside
import me.saket.telephoto.zoomable.internal.unaryMinus
import kotlin.math.abs

/** todo: doc */
@Composable
fun rememberZoomableViewportState(
  maxZoomFactor: Float = 1f,
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
    ) {
      state.refreshContentPosition()
    }

    DisposableEffect(state.unscaledContentLocation) {
      onDispose {
        // Content was changed. Reset everything so that it is moved to its default position.
        // I'm intentionally using a DisposableEffect instead of a LaunchedEffect to avoid
        // running this code when the value is first set, which will break state restoration.
        state.resetContentTransformation()
      }
    }
  }

  return state
}

@Stable
class ZoomableViewportState internal constructor(
  initialTransformation: GestureTransformation? = null
) {

  /**
   * Transformations that should be applied by the viewport to its content.
   *
   * See Modifier#graphicsLayer: [me.saket.telephoto.viewport.graphicsLayer].
   *
   * todo: doc
   */
  val contentTransformation: ZoomableContentTransformation by derivedStateOf {
    gestureTransformation.let {
      ZoomableContentTransformation(
        viewportSize = viewportBounds.size,
        scale = it?.zoom?.finalZoom() ?: ZeroScaleFactor,  // Hide content until an initial zoom value is calculated.
        offset = if (it != null) -it.offset * it.zoom else Offset.Zero,
        rotationZ = 0f,
      )
    }
  }

  // todo: is "gesture" transformation the right name?
  private var gestureTransformation: GestureTransformation? by mutableStateOf(initialTransformation)

  // todo: explain why this isn't a state?
  //  counter-arg: making this a state will allow live edit to work.
  internal var zoomRange = ZoomRange.Default

  internal var contentScale by mutableStateOf<ContentScale>(ContentScale.None)
  internal var contentAlignment by mutableStateOf(Alignment.Center)
  internal lateinit var layoutDirection: LayoutDirection

  /**
   * Raw size of the image/video/anything without any scaling applied.
   * Used only for determining whether the content can zoom any further.
   */
  // TODO: verify doc.
  internal var unscaledContentLocation by mutableStateOf(ZoomableContentLocation.Unspecified)

  /**
   * Bounds of [ZoomableViewport]'s content composable in the layout hierarchy, without any scaling applied.
   */
  // todo: should this be an IntRect?
  // todo: should this be named contentCanvasBounds? or contentCanvasSize?
  internal var contentLayoutBounds by mutableStateOf(Rect.Zero)

  // todo: should this be an IntRect?
  internal var viewportBounds by mutableStateOf(Rect.Zero)

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
          it.coercedIn(zoomRange.inflate(minBy = 0.1f, maxBy = 0.4f))
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

  // todo: doc
  /** Reset content position by discarding the current zoom and offset values. */
  fun resetContentTransformation() {
    gestureTransformation = null
  }

  /** todo: doc */
  fun setContentLocation(location: ZoomableContentLocation) {
    unscaledContentLocation = location
  }

  internal suspend fun handleDoubleTapZoomTo(centroidInViewport: Offset) {
    val start = gestureTransformation ?: return
    val shouldZoomIn = !start.zoom.isAtMaxZoom(zoomRange)

    val targetZoomFactor = if (shouldZoomIn) {
      zoomRange.maxZoom(baseZoomMultiplier = start.zoom.baseZoom)
    } else {
      zoomRange.minZoom(baseZoomMultiplier = start.zoom.baseZoom)
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

  internal suspend fun smoothlySettleOnGestureEnd(velocity: Velocity, density: Density) {
    val start = gestureTransformation!!
    val viewportZoomWithinBounds = start.zoom.coercedIn(zoomRange).viewportZoom
    val isOutOfBounds = start.zoom.viewportZoom != viewportZoomWithinBounds

    if (isOutOfBounds) {
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
    } else {
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

  // todo: should probably test this
  fun coercedIn(range: ZoomRange): ContentZoom {
    return copy(
      baseZoom = baseZoom,
      viewportZoom = viewportZoom.coerceIn(
        minimumValue = range.minZoom(baseZoom) / baseZoom.maxScale,
        maximumValue = range.maxZoom(baseZoom) / baseZoom.maxScale
      )
    )
  }

  fun isAtMinZoom(range: ZoomRange): Boolean {
    return finalZoom().maxScale <= range.minZoom(baseZoomMultiplier = baseZoom)
  }

  fun isAtMaxZoom(range: ZoomRange): Boolean {
    return finalZoom().maxScale >= range.maxZoom(baseZoomMultiplier = baseZoom)
  }
}

// todo: doc.
internal data class ZoomRange(
  private val minZoomAsRatioOfBaseZoom: Float = 1f,
  private val maxZoomAsRatioOfSize: Float,
) {

  fun inflate(minBy: Float, maxBy: Float): ZoomRange {
    return ZoomRange(
      minZoomAsRatioOfBaseZoom = minZoomAsRatioOfBaseZoom - (minBy * minZoomAsRatioOfBaseZoom),
      maxZoomAsRatioOfSize = maxZoomAsRatioOfSize + (maxBy * maxZoomAsRatioOfSize)
    )
  }

  // todo: ZoomRange and ContentZoom are inter-dependent. minZoom() and maxZoom() should probably move to ContentZoom.
  internal fun minZoom(baseZoomMultiplier: ScaleFactor): Float {
    return minZoomAsRatioOfBaseZoom * baseZoomMultiplier.maxScale
  }

  internal fun maxZoom(baseZoomMultiplier: ScaleFactor): Float {
    // Note to self: the max zoom factor can be less than the min zoom
    // factor if the content is scaled-up by default. This can be tested
    // by setting contentScale = CenterCrop.
    return maxOf(maxZoomAsRatioOfSize, minZoom(baseZoomMultiplier))
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
