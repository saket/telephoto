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
import me.saket.telephoto.zoomable.ContentZoom.Companion.ZoomDeltaEpsilon
import me.saket.telephoto.zoomable.GestureTransformation.Companion.ZeroScaleFactor
import me.saket.telephoto.zoomable.ZoomableContentLocation.SameAsLayoutBounds
import me.saket.telephoto.zoomable.internal.TransformableState
import me.saket.telephoto.zoomable.internal.ZoomableSavedState
import me.saket.telephoto.zoomable.internal.div
import me.saket.telephoto.zoomable.internal.maxScale
import me.saket.telephoto.zoomable.internal.roundToIntSize
import me.saket.telephoto.zoomable.internal.times
import me.saket.telephoto.zoomable.internal.topLeftCoercedInside
import me.saket.telephoto.zoomable.internal.unaryMinus
import kotlin.math.abs

/** todo: doc */
@Composable
fun rememberZoomableState(
  maxZoomFactor: Float = 2f,
  autoApplyTransformations: Boolean = true,
): ZoomableState {
  val state = rememberSaveable(saver = ZoomableState.Saver) {
    ZoomableState(
      autoApplyTransformations = autoApplyTransformations
    )
  }.apply {
    zoomRange = ZoomRange(maxZoomAsRatioOfSize = maxZoomFactor)
    layoutDirection = LocalLayoutDirection.current
  }

  if (state.isReadyToInteract) {
    LaunchedEffect(
      state.contentLayoutSize,
      state.contentAlignment,
      state.contentScale,
      state.layoutDirection,
      state.gestureTransformation == null,
    ) {
      state.refreshContentTransformation()
    }
  }
  return state
}

@Stable
class ZoomableState internal constructor(
  initialTransformation: GestureTransformation? = null,
  autoApplyTransformations: Boolean = true,
) {

  /**
   * Transformations that should be applied to [Modifier.zoomable]'s content.
   */
  // todo: doc
  val contentTransformation: ZoomableContentTransformation by derivedStateOf {
    gestureTransformation.let {
      ZoomableContentTransformation(
        contentSize = it?.contentSize ?: Size.Unspecified,
        scale = it?.zoom?.finalZoom() ?: ZeroScaleFactor,  // Hide content until an initial zoom value is calculated.
        offset = if (it != null) -it.offset * it.zoom else Offset.Zero,
        rotationZ = 0f,
        isSpecified = it != null,
      )
    }
  }

  // todo: doc.
  var autoApplyTransformations: Boolean by mutableStateOf(autoApplyTransformations)

  /**
   * Single source of truth for your content's aspect ratio. If you're using `Modifier.zoomable()`
   * with `Image()` or other composables that also accept [ContentScale], they should be set to
   * [ContentScale.None] to avoid any conflicts.
   *
   * A visual guide of the various scale values can be found
   * [here](https://developer.android.com/jetpack/compose/graphics/images/customize#content-scale).
   */
  var contentScale: ContentScale by mutableStateOf(ContentScale.Fit)

  // todo: doc
  //  explain how the alignment affects zooming.
  var contentAlignment: Alignment by mutableStateOf(Alignment.Center)

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
  private var unscaledContentLocation: ZoomableContentLocation by mutableStateOf(SameAsLayoutBounds)

  /**
   * Layout bounds of the zoomable content in the UI hierarchy, without any scaling applied.
   */
  internal var contentLayoutSize by mutableStateOf(Size.Zero)

  /** todo: doc. */
  internal val isReadyToInteract: Boolean by derivedStateOf {
    unscaledContentLocation.isSpecified
      && contentLayoutSize.minDimension != 0f  // Protects against division by zero errors.
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
          val unscaledContentBounds = unscaledContentLocation.calculateBounds(
            layoutSize = contentLayoutSize,
            direction = layoutDirection
          )
          val drawRegionOffset = unscaledContentBounds.topLeft * current.zoom
          newOffset.withZoomAndTranslate(zoom = -current.zoom.finalZoom(), translate = drawRegionOffset) {
            val expectedDrawRegion = Rect(offset = it, size = unscaledContentBounds.size * current.zoom)
            expectedDrawRegion.topLeftCoercedInside(contentLayoutSize, contentAlignment, layoutDirection)
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
      val unscaledContentBounds = unscaledContentLocation.calculateBounds(
        layoutSize = contentLayoutSize,
        direction = layoutDirection
      )

      // This is the minimum scale needed to position the content
      // within its layout bounds w.r.t. its content scale.
      val baseZoom = contentScale.computeScaleFactor(
        srcSize = unscaledContentBounds.size,
        dstSize = contentLayoutSize,
      )

      val oldZoom = ContentZoom(
        baseZoom = baseZoom,
        userZoom = gestureTransformation?.zoom?.userZoom ?: 1f
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
        userZoom = oldZoom.userZoom * zoomDelta
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
            space = contentLayoutSize.roundToIntSize(),
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
          // To ensure that the content always stays within its layout bounds, the content's actual draw
          // region will need to be calculated. This is important because the content's draw region may
          // or may not be equal to its full size. For e.g., a 16:9 image displayed in a 1:2 layout
          // will have empty spaces on both vertical sides.
          val drawRegionOffset = unscaledContentBounds.topLeft * newZoom

          // Note to self: (-offset * zoom) is the final value used for displaying the content composable.
          newOffset.withZoomAndTranslate(zoom = -newZoom.finalZoom(), translate = drawRegionOffset) {
            val expectedDrawRegion = Rect(offset = it, size = unscaledContentBounds.size * newZoom)
            expectedDrawRegion.topLeftCoercedInside(contentLayoutSize, contentAlignment, layoutDirection)
          }
        },
        zoom = newZoom,
        lastCentroid = centroid,
        contentSize = unscaledContentLocation.size(contentLayoutSize),
      )
    }
  )

  private operator fun Offset.div(zoom: ContentZoom): Offset = div(zoom.finalZoom())
  private operator fun Offset.times(zoom: ContentZoom): Offset = times(zoom.finalZoom())
  private operator fun Size.times(zoom: ContentZoom): Size = times(zoom.finalZoom())

  /** todo: doc */
  suspend fun setContentLocation(location: ZoomableContentLocation) {
    unscaledContentLocation = location

    // Refresh content transformation synchronously so that it is available immediately.
    // Otherwise, the old position will be used with this new size and cause a flicker.
    if (isReadyToInteract) {
      refreshContentTransformation()
    }
  }

  /** Reset content to its minimum zoom and zero offset **without** any animation. */
  fun resetZoomImmediately() {
    gestureTransformation = null
  }

  /** Smoothly reset content to its minimum zoom and zero offset **with** animation. */
  suspend fun resetZoom() {
    smoothlyToggleZoom(
      shouldZoomIn = false,
      centroid = Offset.Zero,
    )
  }

  // todo: doc
  /** Update content position by using its current zoom and offset values. */
  internal suspend fun refreshContentTransformation() {
    check(isReadyToInteract)
    transformableState.transform(MutatePriority.PreventUserInput) {
      transformBy(/* default values */)
    }
  }

  internal suspend fun handleDoubleTapZoomTo(centroid: Offset) {
    val start = gestureTransformation ?: return
    smoothlyToggleZoom(
      shouldZoomIn = !start.zoom.isAtMaxZoom(zoomRange),
      centroid = centroid
    )
  }

  private suspend fun smoothlyToggleZoom(
    shouldZoomIn: Boolean,
    centroid: Offset
  ) {
    val start = gestureTransformation ?: return

    val targetZoomFactor = if (shouldZoomIn) {
      zoomRange.maxZoom(baseZoom = start.zoom.baseZoom)
    } else {
      zoomRange.minZoom(baseZoom = start.zoom.baseZoom)
    }
    val targetZoom = start.zoom.copy(
      userZoom = targetZoomFactor / (start.zoom.baseZoom.maxScale)
    )

    // todo: this piece of code is duplicated with onGesture.
    val targetOffset = run {
      val proposedOffset = (start.offset + centroid / start.zoom) - (centroid / targetZoom)
      val unscaledContentBounds = unscaledContentLocation.calculateBounds(
        layoutSize = contentLayoutSize,
        direction = layoutDirection
      )

      val drawRegionOffset = unscaledContentBounds.topLeft * targetZoom

      // Note to self: (-offset * zoom) is the final value used for displaying the content composable.
      proposedOffset.withZoomAndTranslate(zoom = -targetZoom.finalZoom(), translate = drawRegionOffset) {
        val expectedDrawRegion = Rect(offset = it, size = unscaledContentBounds.size * targetZoom)
        expectedDrawRegion.topLeftCoercedInside(contentLayoutSize, contentAlignment, layoutDirection)
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
          userZoom = lerp(
            start = start.zoom.userZoom,
            stop = targetZoom.userZoom,
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

        gestureTransformation = gestureTransformation!!.copy(
          offset = (-animatedOffsetForUi) / animatedZoom,
          zoom = animatedZoom,
          lastCentroid = centroid,
        )
      }
    }
  }

  internal fun isZoomOutsideRange(): Boolean {
    val currentZoom = gestureTransformation!!.zoom
    val userZoomWithinBounds = currentZoom.coercedIn(zoomRange)
    return abs(currentZoom.userZoom - userZoomWithinBounds.userZoom) > ZoomDeltaEpsilon
  }

  internal suspend fun smoothlySettleZoomOnGestureEnd() {
    val start = gestureTransformation!!
    val userZoomWithinBounds = start.zoom.coercedIn(zoomRange).userZoom

    transformableState.transform {
      var previous = start.zoom.userZoom
      AnimationState(initialValue = previous).animateTo(
        targetValue = userZoomWithinBounds,
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
    internal val Saver = Saver<ZoomableState, ZoomableSavedState>(
      save = { ZoomableSavedState(it.gestureTransformation) },
      restore = { ZoomableState(initialTransformation = it.gestureTransformation()) }
    )
  }
}

// todo: doc
internal data class GestureTransformation(
  val offset: Offset,
  val zoom: ContentZoom,
  val lastCentroid: Offset,
  val contentSize: Size,
) {

  companion object {
    val ZeroScaleFactor = ScaleFactor(0f, 0f)
  }
}

// todo: doc
internal data class ContentZoom(
  val baseZoom: ScaleFactor,  // Calculated using ZoomableState's ContentScale.
  val userZoom: Float,        // Zoom applied using gestures.
) {
  // todo: doc
  fun finalZoom(): ScaleFactor {
    return baseZoom * userZoom
  }

  fun coercedIn(
    range: ZoomRange,
    leewayPercentForMinZoom: Float = 0f,
    leewayPercentForMaxZoom: Float = leewayPercentForMinZoom,
  ): ContentZoom {
    val minUserZoom = range.minZoom(baseZoom) / baseZoom.maxScale
    val maxUserZoom = range.maxZoom(baseZoom) / baseZoom.maxScale
    return copy(
      baseZoom = baseZoom,
      userZoom = userZoom.coerceIn(
        minimumValue = minUserZoom * (1 - leewayPercentForMinZoom),
        maximumValue = maxUserZoom * (1 + leewayPercentForMaxZoom),
      )
    )
  }

  fun isAtMinZoom(range: ZoomRange): Boolean {
    return finalZoom().maxScale - range.minZoom(baseZoom = baseZoom) < ZoomDeltaEpsilon
  }

  fun isAtMaxZoom(range: ZoomRange): Boolean {
    return range.maxZoom(baseZoom) - finalZoom().maxScale < ZoomDeltaEpsilon
  }

  companion object {
    const val ZoomDeltaEpsilon = 0.01f
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
