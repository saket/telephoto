package me.saket.telephoto.zoomable

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.GestureTransformation.Companion.ZeroScaleFactor
import me.saket.telephoto.zoomable.internal.div
import me.saket.telephoto.zoomable.internal.maxScale
import me.saket.telephoto.zoomable.internal.times
import me.saket.telephoto.zoomable.internal.topLeftCoercedInside
import me.saket.telephoto.zoomable.internal.unaryMinus

/** todo: doc */
@Composable
fun rememberZoomableViewportState(
  maxZoomFactor: Float = 1f,
): ZoomableViewportState {
  return remember { ZoomableViewportState() }.apply {
    this.zoomRange = ZoomRange(max = maxZoomFactor)
  }
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
        scale = it.zoom?.finalZoom() ?: ZeroScaleFactor,  // Hide content until an initial zoom value is calculated.
        offset = -it.offset * (it.zoom?.finalZoom()?.maxScale ?: 1f),
        rotationZ = 0f,
      )
    }
  }

  private var gestureTransformation by mutableStateOf(GestureTransformation.Empty)
  private var cancelResetAnimation: (() -> Unit)? = null

  internal var zoomRange = ZoomRange.Default  // todo: explain why this isn't a state?

  internal lateinit var contentScale: ContentScale
  internal lateinit var contentAlignment: Alignment

  /**
   * Raw size of the image/video/anything without any scaling applied.
   * Used only for determining whether the content can zoom any further.
   */
  // TODO: verify doc.
  private var unscaledContentSize by mutableStateOf(Size.Unspecified)

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
    unscaledContentSize.isSpecified
      && contentLayoutBounds != Rect.Zero
      && viewportBounds != Rect.Zero
      && ::contentAlignment.isInitialized
  }

  internal val thingsThatAffectContentPosition by derivedStateOf {
    // todo: this doesn't look great.
    // todo: contentAlignment isn't a state!
    Pair(
      contentAlignment to unscaledContentSize,
      Triple(contentLayoutBounds, viewportBounds, contentScale)
    )
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
      cancelResetAnimation?.invoke()
    }

    // TODO: should ZoomableViewport accept ContentScale as a param?
    //  1. It doesn't make a lot of sense to offer ContentScale.Crop for zoomable content.
    //  2. ContentScale.Fill performs a non-uniform scaling. Test that it works correctly.
    val baseContentScale = contentScale.computeScaleFactor(
      srcSize = unscaledContentSize,
      dstSize = contentLayoutBounds.size,
    )

    val oldZoom = gestureTransformation.zoom
      ?: ContentZoom(
        baseZoomMultiplier = baseContentScale,
        viewportZoom = 1f
      )

    val isZoomingOut = zoomDelta < 1f
    val isZoomingIn = zoomDelta > 1f
    val isAtMinZoom = oldZoom.finalZoom().maxScale <= zoomRange.minZoom(baseZoomMultiplier = baseContentScale)
    val isAtMaxZoom = oldZoom.finalZoom().maxScale >= zoomRange.maxZoom(baseZoomMultiplier = baseContentScale)

    // Apply elasticity to zoom once content can't zoom any further.
    val zoomDelta = when {
      isAtMaxZoom && isZoomingIn -> 1f + zoomDelta / 250
      isAtMinZoom && isZoomingOut -> 1f - zoomDelta / 500
      else -> zoomDelta
    }

    val newZoom = ContentZoom(
      baseZoomMultiplier = baseContentScale,
      viewportZoom = oldZoom.viewportZoom * zoomDelta
    )
    val oldOffset = gestureTransformation.offset

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
    // This is comparable to performing a pre-translate + scale + post-translate on a Matrix.
    //
    // I found this maths difficult to understand, so here's another explanation in
    // Ryan Harter's words:
    //
    // The basic idea is that to scale around an arbitrary point, you translate so that that
    // point is in the center, then you rotate, then scale, then move everything back.
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
      offset = newOffset.withZoomAndTranslate(-newZoom.finalZoom(), contentLayoutBounds.topLeft) {
        val newContentBounds = Rect(offset = it, unscaledContentSize * newZoom.finalZoom())
        newContentBounds.topLeftCoercedInside(viewportBounds, contentAlignment)
      },
      zoom = newZoom,
      lastCentroid = centroid,
    )
  }

  private operator fun Offset.div(zoom: ContentZoom): Offset {
    return div(zoom.finalZoom().maxScale)
  }

  internal fun refreshContentPosition() {
    check(isReadyToInteract)
    onGesture(
      centroid = Offset.Zero,
      panDelta = Offset.Zero,
      zoomDelta = 1f,
    )
  }

  /** todo: doc */
  fun setUnscaledContentSize(size: IntSize?) {
    setUnscaledContentSize(size?.toSize())
  }

  /** todo: doc */
  fun setUnscaledContentSize(size: Size?) {
    unscaledContentSize = size ?: Size.Unspecified
  }

  internal suspend fun animateResetOfTransformations() {
    val start = gestureTransformation
    val endViewportZoom = start.zoom!!.coercedIn(zoomRange).viewportZoom

    coroutineScope {
      val animationJob = launch {
        AnimationState(initialValue = 0f).animateTo(
          targetValue = 1f,
          animationSpec = spring()
        ) {
          val current = gestureTransformation
          val targetViewportZoom = lerp(start = start.zoom.viewportZoom, stop = endViewportZoom, fraction = value)

          onGesture(
            centroid = start.lastCentroid,
            panDelta = Offset.Zero,
            zoomDelta = targetViewportZoom / current.zoom!!.viewportZoom,
            cancelAnyOngoingResetAnimation = false,
          )
        }
      }
      cancelResetAnimation = { animationJob.cancel() }
      animationJob.invokeOnCompletion {
        cancelResetAnimation = null
      }
    }
  }
}

private data class GestureTransformation(
  val offset: Offset,
  val zoom: ContentZoom?,
  val lastCentroid: Offset,
) {

  companion object {
    val ZeroScaleFactor = ScaleFactor(0f, 0f)

    val Empty = GestureTransformation(
      offset = Offset.Zero,
      zoom = null,
      lastCentroid = Offset.Zero,
    )
  }
}

internal data class ContentZoom(
  val baseZoomMultiplier: ScaleFactor,
  val viewportZoom: Float,
) {
  // todo: doc
  fun finalZoom(): ScaleFactor {
    return baseZoomMultiplier * viewportZoom
  }

  // todo: should probably test this
  fun coercedIn(limits: ZoomRange): ContentZoom {
    return copy(
      baseZoomMultiplier = baseZoomMultiplier,
      viewportZoom = viewportZoom.coerceIn(
        minimumValue = limits.minZoom(baseZoomMultiplier) / baseZoomMultiplier.maxScale,
        maximumValue = limits.maxZoom(baseZoomMultiplier) / baseZoomMultiplier.maxScale
      )
    )
  }
}

// todo: doc.
//  - it is confusing that both min and max values can be 1f.
@JvmInline
internal value class ZoomRange private constructor(
  private val range: ClosedFloatingPointRange<Float>
) {
  constructor(min: Float = 1f, max: Float) : this(min..max)

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
