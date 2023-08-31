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
import androidx.compose.ui.geometry.isFinite
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
import me.saket.telephoto.zoomable.ZoomableContentLocation.SameAsLayoutBounds
import me.saket.telephoto.zoomable.internal.MutatePriorities
import me.saket.telephoto.zoomable.internal.TransformableState
import me.saket.telephoto.zoomable.internal.Zero
import me.saket.telephoto.zoomable.internal.ZoomableSavedState
import me.saket.telephoto.zoomable.internal.calculateTopLeftToOverlapWith
import me.saket.telephoto.zoomable.internal.deviceInfo
import me.saket.telephoto.zoomable.internal.div
import me.saket.telephoto.zoomable.internal.isPositiveAndFinite
import me.saket.telephoto.zoomable.internal.maxScale
import me.saket.telephoto.zoomable.internal.roundToIntSize
import me.saket.telephoto.zoomable.internal.times
import me.saket.telephoto.zoomable.internal.unaryMinus
import me.saket.telephoto.zoomable.internal.withZoomAndTranslate
import kotlin.math.abs

/**
 * Create a [ZoomableState] that can be used with [Modifier.zoomable].
 *
 * @param zoomSpec See [ZoomSpec.maxZoomFactor] and [ZoomSpec.preventOverOrUnderZoom].
 *
 * @param autoApplyTransformations Determines whether the resulting scale and translation of pan and zoom
 * gestures should be automatically applied to by [Modifier.zoomable] to its content. This can be disabled
 * if your content prefers applying the transformations in a bespoke manner.
 */
@Composable
fun rememberZoomableState(
  zoomSpec: ZoomSpec = ZoomSpec(),
  autoApplyTransformations: Boolean = true,
): ZoomableState {
  val state = rememberSaveable(saver = ZoomableState.Saver) {
    ZoomableState(
      autoApplyTransformations = autoApplyTransformations
    )
  }.also {
    it.zoomSpec = zoomSpec
    it.layoutDirection = LocalLayoutDirection.current
    it.deviceInfo = deviceInfo()
  }

  if (state.isReadyToInteract) {
    LaunchedEffect(
      state.contentLayoutSize,
      state.contentAlignment,
      state.contentScale,
      state.layoutDirection,
      state.rawTransformation == null,
    ) {
      state.refreshContentTransformation()
    }
  }
  return state
}

@Stable
class ZoomableState internal constructor(
  initialTransformation: RawTransformation? = null,
  autoApplyTransformations: Boolean = true,
) {

  /**
   * Transformations that should be applied to [Modifier.zoomable]'s content.
   *
   * See [ZoomableContentTransformation].
   */
  val contentTransformation: ZoomableContentTransformation by derivedStateOf {
    rawTransformation.let {
      val scale = it?.zoom?.finalZoom()
      if (scale != null && scale != ScaleFactor.Zero) {
        ZoomableContentTransformation(
          isSpecified = true,
          contentSize = it.contentSize,
          scale = scale,
          offset = -it.offset * it.zoom,
        )
      } else {
        ZoomableContentTransformation(
          isSpecified = false,
          contentSize = Size.Unspecified,
          scale = ScaleFactor.Zero, // Effectively hide content until an initial zoom value is calculated.
          offset = Offset.Zero,
        )
      }
    }
  }

  /**
   * Determines whether the resulting scale and translation of pan and zoom gestures
   * should be automatically applied to by [Modifier.zoomable] to its content. This can
   * be disabled if your content prefers applying the transformations in a bespoke manner.
   * */
  var autoApplyTransformations: Boolean by mutableStateOf(autoApplyTransformations)

  /**
   * Single source of truth for your content's aspect ratio. If you're using `Modifier.zoomable()`
   * with `Image()` or other composables that also accept [ContentScale], they should not be used
   * to avoid any conflicts.
   *
   * A visual guide of the various scale values can be found
   * [here](https://developer.android.com/jetpack/compose/graphics/images/customize#content-scale).
   */
  var contentScale: ContentScale by mutableStateOf(ContentScale.Fit)

  /**
   * Alignment of the content.
   *
   * When the content is zoomed, it is scaled with respect to this alignment until it
   * is large enough to fill all available space. After that, they're scaled uniformly.
   * */
  var contentAlignment: Alignment by mutableStateOf(Alignment.Center)

  /**
   * The content's current zoom as a fraction of its min and max allowed zoom factors.
   *
   * @return A value between 0 and 1, where 0 indicates that the content is fully zoomed out,
   * 1 indicates that the content is fully zoomed in, and `null` indicates that an initial zoom
   * value hasn't been calculated yet and the content is hidden. A `null` value could be safely
   * treated the same as 0, but [Modifier.zoomable] leaves that decision up to you.
   */
  @get:FloatRange(from = 0.0, to = 1.0)
  val zoomFraction: Float? by derivedStateOf {
    rawTransformation?.let {
      val min = zoomSpec.range.minZoom(it.zoom.baseZoom)
      val max = zoomSpec.range.maxZoom(it.zoom.baseZoom)
      val current = it.zoom.finalZoom().maxScale.coerceIn(min, max)
      when {
        current == min && min == max -> 1f  // Content can't zoom.
        else -> ((current - min) / (max - min)).coerceIn(0f, 1f)
      }
    }
  }

  internal var rawTransformation: RawTransformation? by mutableStateOf(initialTransformation)

  internal var zoomSpec by mutableStateOf(ZoomSpec())
  internal var layoutDirection: LayoutDirection by mutableStateOf(LayoutDirection.Ltr)
  internal var deviceInfo: String = "uninitialized"

  /**
   * Raw size of the zoomable content without any scaling applied.
   * Used to ensure that the content does not pan/zoom outside its limits.
   */
  private var unscaledContentLocation: ZoomableContentLocation by mutableStateOf(SameAsLayoutBounds)

  /**
   * Layout bounds of the zoomable content in the UI hierarchy, without any scaling applied.
   */
  internal var contentLayoutSize by mutableStateOf(Size.Zero)

  private val unscaledContentBounds by derivedStateOf {
    unscaledContentLocation.location(
      layoutSize = contentLayoutSize,
      direction = layoutDirection
    )
  }

  /** Whether sufficient information is available about the content to start listening to pan & zoom gestures. */
  internal val isReadyToInteract: Boolean by derivedStateOf {
    unscaledContentLocation.isSpecified
      && contentLayoutSize.minDimension != 0f  // Protects against division by zero errors.
  }

  @Suppress("NAME_SHADOWING")
  internal val transformableState = TransformableState { zoomDelta, panDelta, _, centroid ->
    check(panDelta.isFinite && zoomDelta.isFinite() && centroid.isFinite) {
      "Can't transform with zoomDelta=$zoomDelta, panDelta=$panDelta, centroid=$centroid. ${collectDebugInfoForIssue41()}"
    }

    // This is the minimum scale needed to position the content
    // within its layout bounds w.r.t. its content scale.
    val baseZoom = contentScale.computeScaleFactor(
      srcSize = unscaledContentBounds.size,
      dstSize = contentLayoutSize,
    )
    check(baseZoom.isPositiveAndFinite()) {
      "Old zoom is invalid/infinite. ${collectDebugInfoForIssue41()}"
    }

    val oldZoom = ContentZoom(
      baseZoom = baseZoom,
      userZoom = rawTransformation?.zoom?.userZoom ?: 1f
    )
    check(oldZoom.finalZoom().isPositiveAndFinite()) {
      "Old zoom is invalid/infinite. ${collectDebugInfoForIssue41()}"
    }

    val isZoomingOut = zoomDelta < 1f
    val isZoomingIn = zoomDelta > 1f

    // Apply elasticity if content is being over/under-zoomed.
    val isAtMaxZoom = oldZoom.isAtMaxZoom(zoomSpec.range)
    val isAtMinZoom = oldZoom.isAtMinZoom(zoomSpec.range)
    val zoomDelta = when {
      !zoomSpec.preventOverOrUnderZoom -> zoomDelta
      isZoomingIn && isAtMaxZoom -> 1f + zoomDelta / 250
      isZoomingOut && isAtMinZoom -> 1f - zoomDelta / 250
      else -> zoomDelta
    }
    val newZoom = ContentZoom(
      baseZoom = baseZoom,
      userZoom = oldZoom.userZoom * zoomDelta
    ).let {
      if (zoomSpec.preventOverOrUnderZoom && (isAtMinZoom || isAtMaxZoom)) {
        // Apply a hard-stop after a limit.
        it.coercedIn(
          range = zoomSpec.range,
          leewayPercentForMinZoom = 0.1f,
          leewayPercentForMaxZoom = 0.4f
        )
      } else {
        it
      }
    }

    val oldOffset = rawTransformation.let {
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

    rawTransformation = RawTransformation(
      offset = oldOffset
        .retainCentroidPositionAfterZoom(
          centroid = centroid,
          panDelta = panDelta,
          oldZoom = oldZoom,
          newZoom = newZoom
        )
        .coerceWithinBounds(proposedZoom = newZoom),
      zoom = newZoom,
      lastCentroid = centroid,
      contentSize = unscaledContentLocation.size(contentLayoutSize),
    )
  }

  internal fun canConsumePanChange(panDelta: Offset): Boolean {
    val current = rawTransformation
      ?: return false // Content is probably not ready yet. Ignore this gesture.

    val panDeltaWithZoom = panDelta / current.zoom
    val newOffset = (current.offset - panDeltaWithZoom)
    val newOffsetWithinBounds = newOffset.coerceWithinBounds(proposedZoom = current.zoom)

    val consumedPan = panDeltaWithZoom - (newOffsetWithinBounds - newOffset)
    val isHorizontalPan = abs(panDeltaWithZoom.x) > abs(panDeltaWithZoom.y)

    return (if (isHorizontalPan) abs(consumedPan.x) else abs(consumedPan.y)) > ZoomDeltaEpsilon
  }

  /**
   * Translate this offset such that the visual position of [centroid]
   * remains the same after applying [panDelta] and [newZoom].
   */
  private fun Offset.retainCentroidPositionAfterZoom(
    centroid: Offset,
    panDelta: Offset = Offset.Zero,
    oldZoom: ContentZoom,
    newZoom: ContentZoom,
  ): Offset {
    check(this.isFinite) {
      "Can't center around an infinite offset ${collectDebugInfoForIssue41()}"
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
    //     Move the centroid to the center
    //         of panned content(?)
    //                  |                       Scale
    //                  |                         |                Move back
    //                  |                         |           (+ new translation)
    //                  |                         |                    |
    //     _____________|_____________    ________|_________   ________|_________
    return ((this + centroid / oldZoom) - (centroid / newZoom + panDelta / oldZoom)).also {
      check(it.isFinite) {
        val debugInfo = collectDebugInfoForIssue41(
          "centroid" to centroid,
          "panDelta" to panDelta,
          "oldZoom" to oldZoom,
          "newZoom" to newZoom,
        )
        "retainCentroidPositionAfterZoom() generated an infinite value. $debugInfo"
      }
    }
  }

  private fun Offset.coerceWithinBounds(proposedZoom: ContentZoom): Offset {
    check(this.isFinite) {
      "Can't coerce an infinite offset ${collectDebugInfoForIssue41("proposedZoom" to proposedZoom)}"
    }

    val scaledTopLeft = unscaledContentBounds.topLeft * proposedZoom

    // Note to self: (-offset * zoom) is the final value used for displaying the content composable.
    return withZoomAndTranslate(zoom = -proposedZoom.finalZoom(), translate = scaledTopLeft) {
      val expectedDrawRegion = Rect(offset = it, size = unscaledContentBounds.size * proposedZoom)
      expectedDrawRegion.calculateTopLeftToOverlapWith(contentLayoutSize, contentAlignment, layoutDirection)
    }
  }

  private operator fun Offset.div(zoom: ContentZoom): Offset = div(zoom.finalZoom())
  private operator fun Offset.times(zoom: ContentZoom): Offset = times(zoom.finalZoom())
  private operator fun Size.times(zoom: ContentZoom): Size = times(zoom.finalZoom())

  /** See [ZoomableContentLocation]. */
  suspend fun setContentLocation(location: ZoomableContentLocation) {
    unscaledContentLocation = location

    // Refresh content transformation synchronously so that the result is available immediately.
    // Otherwise, the old position will be used with this new size and cause a flicker.
    refreshContentTransformation()
  }

  /** Reset content to its minimum zoom and zero offset. */
  suspend fun resetZoom(withAnimation: Boolean = true) {
    if (withAnimation) {
      smoothlyToggleZoom(
        shouldZoomIn = false,
        centroid = Offset.Zero,
      )
    } else {
      rawTransformation = null  // todo: use mutex.
    }
  }

  /**
   * Update the content's position. This is called when values
   * such as [contentScale] and [contentAlignment] are updated.
   */
  internal suspend fun refreshContentTransformation() {
    if (isReadyToInteract) {
      transformableState.transform(MutatePriority.PreventUserInput) {
        transformBy(/* default values */)
      }
    }
  }

  internal suspend fun handleDoubleTapZoomTo(centroid: Offset) {
    val start = rawTransformation ?: return
    smoothlyToggleZoom(
      shouldZoomIn = !start.zoom.isAtMaxZoom(zoomSpec.range),
      centroid = centroid
    )
  }

  private suspend fun smoothlyToggleZoom(
    shouldZoomIn: Boolean,
    centroid: Offset
  ) {
    val start = rawTransformation ?: return

    val targetZoomFactor = if (shouldZoomIn) {
      zoomSpec.range.maxZoom(baseZoom = start.zoom.baseZoom)
    } else {
      zoomSpec.range.minZoom(baseZoom = start.zoom.baseZoom)
    }
    val targetZoom = start.zoom.copy(
      userZoom = targetZoomFactor / (start.zoom.baseZoom.maxScale)
    )

    val targetOffset = start.offset
      .retainCentroidPositionAfterZoom(
        centroid = centroid,
        oldZoom = start.zoom,
        newZoom = targetZoom,
      )
      .coerceWithinBounds(proposedZoom = targetZoom)

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

        rawTransformation = rawTransformation!!.copy(
          offset = (-animatedOffsetForUi) / animatedZoom,
          zoom = animatedZoom,
          lastCentroid = centroid,
        )
      }
    }
  }

  internal fun isZoomOutsideRange(): Boolean {
    val currentZoom = rawTransformation!!.zoom
    val userZoomWithinBounds = currentZoom.coercedIn(zoomSpec.range)
    return abs(currentZoom.userZoom - userZoomWithinBounds.userZoom) > ZoomDeltaEpsilon
  }

  internal suspend fun smoothlySettleZoomOnGestureEnd() {
    val start = rawTransformation!!
    val userZoomWithinBounds = start.zoom.coercedIn(zoomSpec.range).userZoom

    transformableState.transform(MutatePriority.Default) {
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
    check(velocity.x.isFinite() && velocity.y.isFinite()) { "Invalid velocity = $velocity" }

    val start = rawTransformation!!
    transformableState.transform(MutatePriorities.FlingAnimation) {
      var previous = start.offset
      AnimationState(
        typeConverter = Offset.VectorConverter,
        initialValue = previous,
        initialVelocityVector = AnimationVector(velocity.x, velocity.y)
      ).animateDecay(splineBasedDecay(density)) {
        transformBy(
          centroid = start.lastCentroid,
          panChange = (value - previous).also {
            check(it.isFinite) {
              val debugInfo = collectDebugInfoForIssue41(
                "value" to value,
                "previous" to previous,
                "velocity" to velocity,
              )
              "Can't fling with an invalid pan = $it. $debugInfo"
            }
          }
        )
        previous = value
      }
    }
  }

  // https://github.com/saket/telephoto/issues/41
  private fun collectDebugInfoForIssue41(vararg extras: Pair<String, Any>): String {
    return buildString {
      appendLine()
      extras.forEach { (key, value) ->
        appendLine("$key = $value")
      }
      appendLine("rawTransformation = $rawTransformation")
      appendLine("contentTransformation = $contentTransformation")
      appendLine("contentScale = $contentScale")
      appendLine("contentAlignment = $contentAlignment")
      appendLine("isReadyToInteract = $isReadyToInteract")
      appendLine("unscaledContentLocation = $unscaledContentLocation")
      appendLine("unscaledContentBounds = $unscaledContentBounds")
      appendLine("contentLayoutSize = $contentLayoutSize")
      appendLine("zoomSpec = $zoomSpec")
      append(deviceInfo)
      appendLine("Please share this error message to https://github.com/saket/telephoto/issues/41?")
    }
  }

  companion object {
    internal val Saver = Saver<ZoomableState, ZoomableSavedState>(
      save = { ZoomableSavedState(it.rawTransformation) },
      restore = { ZoomableState(initialTransformation = it.gestureTransformation()) }
    )
  }
}

/** An intermediate, non-normalized model used for generating [ZoomableContentTransformation]. */
internal data class RawTransformation(
  val offset: Offset,
  val zoom: ContentZoom,
  val lastCentroid: Offset,
  val contentSize: Size,
)

internal data class ContentZoom(
  /**
   * The initial scale needed to fit [Modifier.zoomable]'s content with
   * respect to [ZoomableState.contentScale].
   * */
  val baseZoom: ScaleFactor,

  /** Zoom applied by user using gestures. */
  val userZoom: Float,
) {
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
    /** Differences below this value are ignored when comparing two zoom values. */
    const val ZoomDeltaEpsilon = 0.01f
  }
}

internal data class ZoomRange(
  private val minZoomAsRatioOfBaseZoom: Float = 1f,
  private val maxZoomAsRatioOfSize: Float,
) {

  internal fun minZoom(baseZoom: ScaleFactor): Float {
    return minZoomAsRatioOfBaseZoom * baseZoom.maxScale
  }

  internal fun maxZoom(baseZoom: ScaleFactor): Float {
    // Note to self: the max zoom factor can be less than the min zoom
    // factor if the content is scaled-up by default. This can be tested
    // by setting contentScale = CenterCrop.
    return maxOf(maxZoomAsRatioOfSize, minZoom(baseZoom))
  }
}
