package me.saket.telephoto.viewport

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring.StiffnessMediumLow
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.saket.telephoto.viewport.GestureTransformation.Companion.ZeroScaleFactor
import me.saket.telephoto.viewport.internal.div
import me.saket.telephoto.viewport.internal.maxScale
import me.saket.telephoto.viewport.internal.roundToIntSize
import me.saket.telephoto.viewport.internal.times
import me.saket.telephoto.viewport.internal.topLeftCoercedInside
import me.saket.telephoto.viewport.internal.unaryMinus

/** todo: doc */
@Composable
fun rememberZoomableViewportState(
  maxZoomFactor: Float = 1f,
): ZoomableViewportState {
  val state = remember {
    ZoomableViewportState()
  }.also {
    it.zoomRange = ZoomRange(max = maxZoomFactor)
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
  }

  return state
}

@Stable
class ZoomableViewportState internal constructor() {
  /**
   * Transformations that should be applied by the viewport to its content.
   *
   * todo: doc
   */
  val contentTransformation: ZoomableContentTransformation by derivedStateOf {
    gestureTransformation.let {
      ZoomableContentTransformation(
        viewportSize = viewportBounds.size,
        scale = it?.zoom?.finalZoom() ?: ZeroScaleFactor,  // Hide content until an initial zoom value is calculated.
        offset = if (it != null) -it.offset * it.zoom.finalZoom().maxScale else Offset.Zero,
        rotationZ = 0f,
      )
    }
  }

  private var gestureTransformation: GestureTransformation? by mutableStateOf(null)
  private var cancelOngoingAnimation: (() -> Unit)? = null

  // todo: explain why this isn't a state?
  //  counter-arg: making this a state will allow live edit to work.
  internal var zoomRange = ZoomRange.Default

  internal lateinit var contentScale: ContentScale
  internal lateinit var contentAlignment: Alignment
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
      && ::contentAlignment.isInitialized
  }

  @Suppress("NAME_SHADOWING")
  internal fun onGesture(
    centroid: Offset,
    panDelta: Offset,
    zoomDelta: Float,
    rotationDelta: Float = 0f,
    cancelAnyOngoingResetAnimation: Boolean = true
  ) {
    if (cancelAnyOngoingResetAnimation) {
      cancelOngoingAnimation?.invoke()
    }

    val unscaledContentBounds = unscaledContentLocation.boundsIn(
      parent = contentLayoutBounds,
      direction = layoutDirection
    )

    // This is the minimum scale needed to position the
    // content within its viewport w.r.t. its content scale.
    val baseZoomMultiplier = contentScale.computeScaleFactor(
      srcSize = unscaledContentBounds.size,
      dstSize = contentLayoutBounds.size,
    )

    val oldZoom = gestureTransformation?.zoom
      ?: ContentZoom(
        baseZoomMultiplier = baseZoomMultiplier,
        viewportZoom = 1f
      )

    val isZoomingOut = zoomDelta < 1f
    val isZoomingIn = zoomDelta > 1f

    // Apply elasticity to zoom once content can't zoom any further.
    val zoomDelta = when {
      isZoomingIn && oldZoom.isAtMaxZoom(zoomRange) -> 1f + zoomDelta / 250
      isZoomingOut && oldZoom.isAtMinZoom(zoomRange) -> 1f - zoomDelta / 500
      else -> zoomDelta
    }
    val newZoom = ContentZoom(
      baseZoomMultiplier = baseZoomMultiplier,
      viewportZoom = oldZoom.viewportZoom * zoomDelta
    )

    val oldOffset = gestureTransformation.let {
      if (it != null) {
        it.offset
      } else {
        val alignedToCenter = contentAlignment.align(
          size = (unscaledContentBounds.size * baseZoomMultiplier).roundToIntSize(),
          space = viewportBounds.size.roundToIntSize(),
          layoutDirection = layoutDirection
        )
        -alignedToCenter.toOffset() / oldZoom.finalZoom()
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
        val drawRegionOffset = contentLayoutBounds.topLeft + (unscaledContentBounds.topLeft * newZoom.finalZoom())

        // Note to self: (-offset * zoom) is the final value used for displaying the content composable.
        newOffset.withZoomAndTranslate(zoom = -newZoom.finalZoom(), translate = drawRegionOffset) {
          val expectedDrawRegion = Rect(offset = it, size = unscaledContentBounds.size * newZoom.finalZoom())
          expectedDrawRegion.topLeftCoercedInside(viewportBounds, contentAlignment, layoutDirection)
        }
      },
      zoom = newZoom,
      lastCentroid = centroid,
    )
  }

  private operator fun Offset.div(zoom: ContentZoom): Offset {
    return div(zoom.finalZoom().maxScale)
  }

  // todo: doc
  internal fun refreshContentPosition() {
    check(isReadyToInteract)
    onGesture(
      centroid = Offset.Zero,
      panDelta = Offset.Zero,
      zoomDelta = 1f,
    )
  }

  // todo: doc
  fun resetContentTransformation() {
    gestureTransformation = null
    if (isReadyToInteract) {
      refreshContentPosition()
    }
  }

  /** todo: doc */
  fun setContentLocation(location: ZoomableContentLocation) {
    unscaledContentLocation = location

    // Content was changed. Reset everything so
    // that it is moved to its default position.
    resetContentTransformation()
  }

  internal suspend fun handleDoubleTapZoomTo(centroidInViewport: Offset) {
    cancelOngoingAnimation?.invoke()

    val start = gestureTransformation ?: return
    val shouldZoomIn = !start.zoom.isAtMaxZoom(zoomRange)

    val targetZoomFactor = if (shouldZoomIn) {
      zoomRange.maxZoom(baseZoomMultiplier = start.zoom.baseZoomMultiplier)
    } else {
      zoomRange.minZoom(baseZoomMultiplier = start.zoom.baseZoomMultiplier)
    }
    val targetZoom = start.zoom.copy(
      viewportZoom = targetZoomFactor / (start.zoom.baseZoomMultiplier.maxScale)
    )

    // todo: this piece of code is duplicated with onGesture.
    val targetOffset = run {
      val proposedOffset = (start.offset + centroidInViewport / start.zoom) - (centroidInViewport / targetZoom)
      val unscaledContentBounds = unscaledContentLocation.boundsIn(
        parent = contentLayoutBounds,
        direction = layoutDirection
      )

      val drawRegionOffset = contentLayoutBounds.topLeft + (unscaledContentBounds.topLeft * targetZoom.finalZoom())

      // Note to self: (-offset * zoom) is the final value used for displaying the content composable.
      proposedOffset.withZoomAndTranslate(zoom = -targetZoom.finalZoom(), translate = drawRegionOffset) {
        val expectedDrawRegion = Rect(offset = it, size = unscaledContentBounds.size * targetZoom.finalZoom())
        expectedDrawRegion.topLeftCoercedInside(viewportBounds, contentAlignment, layoutDirection)
      }
    }

    coroutineScope {
      val animationJob = launch {
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
            start = (-start.offset * start.zoom.finalZoom()),
            stop = (-targetOffset * targetZoom.finalZoom()),
            fraction = value
          )

          gestureTransformation = GestureTransformation(
            offset = (-animatedOffsetForUi) / animatedZoom,
            zoom = animatedZoom,
            lastCentroid = centroidInViewport,
          )
        }
      }
      cancelOngoingAnimation = { animationJob.cancel() }
      animationJob.invokeOnCompletion {
        cancelOngoingAnimation = null
      }
    }
  }

  internal suspend fun smoothlySettleOnGestureEnd() {
    val start = gestureTransformation!!
    val targetViewportZoom = start.zoom.coercedIn(zoomRange).viewportZoom

    coroutineScope {
      val animationJob = launch {
        AnimationState(initialValue = 0f).animateTo(
          targetValue = 1f,
          animationSpec = spring()
        ) {
          val current = gestureTransformation!!
          val newViewportZoom = lerp(start = start.zoom.viewportZoom, stop = targetViewportZoom, fraction = value)
          onGesture(
            centroid = start.lastCentroid,
            panDelta = Offset.Zero,
            zoomDelta = newViewportZoom / current.zoom.viewportZoom,
            cancelAnyOngoingResetAnimation = false,
          )
        }
      }
      cancelOngoingAnimation = { animationJob.cancel() }
      animationJob.invokeOnCompletion {
        cancelOngoingAnimation = null
      }
    }
  }
}

// todo: doc
private data class GestureTransformation(
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
  val baseZoomMultiplier: ScaleFactor,
  val viewportZoom: Float,
) {
  // todo: doc
  fun finalZoom(): ScaleFactor {
    return baseZoomMultiplier * viewportZoom
  }

  // todo: should probably test this
  fun coercedIn(range: ZoomRange): ContentZoom {
    return copy(
      baseZoomMultiplier = baseZoomMultiplier,
      viewportZoom = viewportZoom.coerceIn(
        minimumValue = range.minZoom(baseZoomMultiplier) / baseZoomMultiplier.maxScale,
        maximumValue = range.maxZoom(baseZoomMultiplier) / baseZoomMultiplier.maxScale
      )
    )
  }

  fun isAtMinZoom(range: ZoomRange): Boolean {
    return finalZoom().maxScale <= range.minZoom(baseZoomMultiplier = baseZoomMultiplier)
  }

  fun isAtMaxZoom(range: ZoomRange): Boolean {
    return finalZoom().maxScale >= range.maxZoom(baseZoomMultiplier = baseZoomMultiplier)
  }
}

// todo: doc.
//  - it is confusing that both min and max values can be 1f.
@JvmInline
internal value class ZoomRange private constructor(
  private val range: ClosedFloatingPointRange<Float>
) {
  constructor(min: Float = 1f, max: Float) : this(min..max)

  // todo: ZoomRange and ContentZoom are inter-dependent. minZoom() and maxZoom() should probably move to ContentZoom.
  internal fun minZoom(baseZoomMultiplier: ScaleFactor): Float {
    return range.start * baseZoomMultiplier.maxScale
  }

  internal fun maxZoom(baseZoomMultiplier: ScaleFactor): Float {
    // Max zoom factor could end up being less than min zoom factor if the content is
    // scaled-up by default. This is easy to test by setting contentScale = CenterCrop.
    return maxOf(range.endInclusive, minZoom(baseZoomMultiplier))
  }

  companion object {
    val Default = ZoomRange(min = 1f, max = 1f)
  }
}

// todo: improve doc.
/** This is named along the lines of `Canvas#withTranslate()`. */
private fun Offset.withZoomAndTranslate(zoom: ScaleFactor, translate: Offset, action: (Offset) -> Offset): Offset {
  return (action((this * zoom) + translate) - translate) / zoom
}
