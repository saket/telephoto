@file:OptIn(ExperimentalTelephotoApi::class)
@file:Suppress("ConstPropertyName")

package me.saket.telephoto.zoomable

import androidx.compose.animation.core.*
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.times
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.AbsoluteZoomFactor.Companion.isAtMaxZoom
import me.saket.telephoto.zoomable.AbsoluteZoomFactor.Companion.isAtMinZoom
import me.saket.telephoto.zoomable.ZoomableContentLocation.SameAsLayoutBounds
import me.saket.telephoto.zoomable.internal.*
import me.saket.telephoto.zoomable.spatial.*
import kotlin.jvm.JvmInline
import kotlin.math.abs

@Stable
internal class RealZoomableState internal constructor(
  savedState: SavedZoomableState,
) : ZoomableState {

  override val contentTransformation: ZoomableContentTransformation by derivedStateOf {
    val gestureStateInputs = currentGestureStateInputs
    if (gestureStateInputs != null) {
      RealZoomableContentTransformation.calculateFrom(
        gestureStateInputs = gestureStateInputs,
        gestureState = gestureState.calculate(gestureStateInputs),
        coordinateSystem = coordinateSystem,
      )
    } else {
      RealZoomableContentTransformation.Unspecified
    }
  }

  override val zoomFraction: Float? by derivedStateOf {
//    val gestureStateInputs = currentGestureStateInputs
//    if (gestureStateInputs != null) {
//      val gestureState = gestureState.calculate(gestureStateInputs)
//      val baseZoomFactor = gestureStateInputs.baseZoom
//      val min = AbsoluteZoomFactor.minimum(baseZoomFactor, zoomSpec.range).userZoom.value
//      val max = AbsoluteZoomFactor.maximum(baseZoomFactor, zoomSpec.range).userZoom.value
//      val current = gestureState.userZoom.value.coerceIn(min, max)
//      when {
//        current == min && min == max -> 1f  // Content can't zoom.
//        else -> ((current - min) / (max - min)).coerceIn(0f, 1f)
//      }
//    } else {
//      null
//    }
    0f
  }

  override var autoApplyTransformations: Boolean by mutableStateOf(savedState.autoApplyTransformations)
  override var contentScale: ContentScale by mutableStateOf(ContentScale.Fit)
  override var contentAlignment: Alignment by mutableStateOf(Alignment.Center)
  override var contentPadding: PaddingValues by mutableStateOf(PaddingValues(0.dp))
  override var isAnimationRunning: Boolean by mutableStateOf(false)
  override val coordinateSystem = RealZoomableCoordinateSystem(this)

  internal var hardwareShortcutsSpec by mutableStateOf(HardwareShortcutsSpec())
  internal var layoutDirection: LayoutDirection by mutableStateOf(LayoutDirection.Ltr)
  internal var density: Density? by mutableStateOf(null)

  /**
   * Raw size of the zoomable content without any scaling applied.
   * Used to ensure that the content does not pan/zoom outside its limits.
   */
  private var unscaledContentLocation: ZoomableContentLocation by mutableStateOf(SameAsLayoutBounds)

  /**
   * Layout bounds of the zoomable content in the UI hierarchy, without any scaling applied.
   */
  internal var viewportSize: Size by mutableStateOf(Size.Unspecified)

  internal var dynamicZoomSpec: DynamicZoomSpec by mutableStateOf(DynamicZoomSpec.recommend(ZoomSpec()))
  override val zoomSpec: ZoomSpec get() = currentGestureStateInputs?.zoomSpec ?: ZoomSpec()

  internal var gestureState: GestureStateCalculator by mutableStateOf(
    GestureStateCalculator { inputs ->
      savedState.gestureState?.restore(
        inputs = inputs,
        coerceOffsetWithinBounds = { contentOffset, contentZoom ->
          contentOffset.coerceWithinContentBounds(contentZoom, inputs)
        }
      )
        ?: GestureState(
          userZoom = UserZoomFactor(1f),
          userOffset = UserOffset(Offset.Zero),
          lastCentroid = inputs.viewportSize.center,
        )
    }
  )

  private val gestureStateInputsCalculator: GestureStateInputsCalculator by derivedStateOf {
    GestureStateInputsCalculator { viewportSize ->
      val contentPadding = density?.let { density ->
        this.contentPadding.resolve(density, layoutDirection)
      }
      if (
        viewportSize.isUnspecifiedOrEmpty ||
        unscaledContentLocation == ZoomableContentLocation.Unspecified ||
        contentPadding == null
      ) {
        return@GestureStateInputsCalculator null
      }

      val unscaledContentBounds = unscaledContentLocation.location(
        layoutSize = viewportSize,
        direction = layoutDirection,
      )
      if (unscaledContentBounds.size.isUnspecifiedOrEmpty) {
        return@GestureStateInputsCalculator null
      }

      val paddedViewportBounds = Rect(
        offset = contentPadding.topLeft,
        size = viewportSize - contentPadding.size,
      )

      val baseZoomFactor = contentScale.computeScaleFactor(
        srcSize = unscaledContentBounds.size,
        dstSize = paddedViewportBounds.size,
      )
      check(baseZoomFactor != ScaleFactor.Zero) {
        "Base zoom shouldn't be zero. content bounds = $unscaledContentBounds, viewport size = $viewportSize"
      }
      val baseOffset = run {
        // todo: it should be possible to reuse Rect#calculateTopLeftToOverlapWith() here.
        val alignmentOffset = paddedViewportBounds.topLeft + contentAlignment.align(
          size = (unscaledContentBounds.size * baseZoomFactor).roundToIntSize(),
          space = paddedViewportBounds.size.roundToIntSize(),
          layoutDirection = layoutDirection,
        ).toOffset()
        // Take the content's top-left into account because it may not start at 0,0.
        unscaledContentBounds.topLeft + (-alignmentOffset / baseZoomFactor)
      }
      GestureStateInputs(
        viewportSize = viewportSize,
        paddedViewportBounds = paddedViewportBounds,
        baseZoom = BaseZoomFactor(baseZoomFactor),
        baseOffset = baseOffset,
        unscaledContentBounds = unscaledContentBounds,
        contentAlignment = contentAlignment,
        layoutDirection = layoutDirection,
        zoomSpec = with(dynamicZoomSpec) {
          RealDynamicZoomSpecScope.compute(
            DynamicZoomSpecInputs(
              unscaledContentSize = unscaledContentBounds.size,
              scaledContentBounds = unscaledContentBounds.zoomedAndTranslatedBy(baseZoomFactor, baseOffset),
              paddedViewportBounds = paddedViewportBounds,
            )
          )
        }
      )
    }
  }

  internal val currentGestureStateInputs: GestureStateInputs? by derivedStateOf {
    gestureStateInputsCalculator.calculate(viewportSize)
  }

  /** See [PlaceholderBoundsProvider]. */
  internal var placeholderBoundsProvider: PlaceholderBoundsProvider? by mutableStateOf(null)

  @Suppress("OVERRIDE_DEPRECATION")
  override val transformedContentBounds: Rect by derivedStateOf {
    transformUnscaledContentBoundsBy(clipToViewport = false) { _, transformation ->
      zoomedAndTranslatedBy(transformation.scale, transformation.offset)
    } ?: Rect.Zero
  }

  /**
   * Whether sufficient information is available about the content to start listening
   * to pan & zoom gestures.
   */
  internal val isReadyForInteraction: Boolean
    get() = currentGestureStateInputs != null

  @Suppress("NAME_SHADOWING")
  internal val transformableState = TransformableState { zoomDelta, panDelta, _, centroid ->
    check(panDelta.isSpecifiedAndFinite() && zoomDelta.isFinite() && centroid.isSpecifiedAndFinite()) {
      "Can't transform with zoomDelta=$zoomDelta, panDelta=$panDelta, centroid=$centroid. ${collectDebugInfo()}"
    }

    val lastGestureState = calculateGestureState() ?: return@TransformableState
    gestureState = GestureStateCalculator { inputs ->
      val oldZoom = AbsoluteZoomFactor(
        baseZoom = inputs.baseZoom,
        userZoom = lastGestureState.userZoom,
      )
      check(oldZoom.finalZoom().isPositiveAndFinite()) {
        "Old zoom is invalid/infinite. ${collectDebugInfo(gestureState = lastGestureState)}"
      }

      val isZoomingOut = zoomDelta < 1f
      val isZoomingIn = zoomDelta > 1f
      val isAtMaxZoom = oldZoom.userZoom.isAtMaxZoom(coordinateSystem, zoomSpec.range)
      val isAtMinZoom = oldZoom.userZoom.isAtMinZoom(coordinateSystem, zoomSpec.range)

      println("zoom by = $zoomDelta, is at max? $isAtMaxZoom, is at min? $isAtMinZoom")

      // Apply overzoom effect if content is being over/under-zoomed.
      val zoomDelta = when {
        isZoomingIn && isAtMaxZoom -> zoomSpec.maximum.overzoomEffect.adjust(zoomDelta)
        isZoomingOut && isAtMinZoom -> zoomSpec.minimum.overzoomEffect.adjust(zoomDelta)
        else -> zoomDelta
      }
      val newZoom = AbsoluteZoomFactor(
        baseZoom = inputs.baseZoom,
        userZoom = oldZoom.userZoom * zoomDelta,
      ).let {
        // Disable overzooms after a certain extent.
        if (
          (isAtMaxZoom && zoomSpec.maximum.overzoomEffect != OverzoomEffect.NoLimits)
          || (isAtMinZoom && zoomSpec.minimum.overzoomEffect != OverzoomEffect.NoLimits)
        ) {
          it.coerceUserZoomIn(
            range = zoomSpec.range,
            leewayPercentForMinZoom = 0.1f,
            leewayPercentForMaxZoom = 0.4f
          )
        } else {
          it
        }
      }
      check(newZoom.finalZoom().let { it.isPositiveAndFinite() && it.minScale > 0f }) {
        "New zoom is invalid/infinite = $newZoom. ${collectDebugInfo("zoomDelta" to zoomDelta)}"
      }

      val oldOffset = AbsoluteOffset(
        baseOffset = inputs.baseOffset,
        userOffset = lastGestureState.userOffset,
      )
      GestureState(
        userOffset = oldOffset
          .retainCentroidPositionAfterZoom(
            centroid = centroid,
            panDelta = panDelta,
            oldZoom = oldZoom,
            newZoom = newZoom,
          )
          .coerceWithinContentBounds(proposedZoom = newZoom, inputs = inputs)
          .userOffset,
        userZoom = newZoom.userZoom,
        lastCentroid = centroid,
      )
    }
  }

  private suspend fun awaitUntilIsReadyForInteraction() {
    if (!isReadyForInteraction) {
      snapshotFlow { isReadyForInteraction }.first { ready -> ready }
    }
  }

  internal fun canConsumePanChange(panDelta: Offset): Boolean {
    val gestureStateInputs = currentGestureStateInputs ?: return false // Content isn't ready yet.
    val current = gestureState.calculate(gestureStateInputs)

    val currentZoom = AbsoluteZoomFactor(gestureStateInputs.baseZoom, current.userZoom)
    val panDeltaWithZoom = panDelta / currentZoom
    val targetOffset = AbsoluteOffset(
      baseOffset = gestureStateInputs.baseOffset,
      userOffset = current.userOffset - panDeltaWithZoom,
    )
    check(targetOffset.isFinite) {
      "Offset can't be infinite ${collectDebugInfo("panDelta" to panDelta)}"
    }

    val targetOffsetWithinBounds = targetOffset.coerceWithinContentBounds(
      proposedZoom = currentZoom,
      inputs = gestureStateInputs,
    )
    val consumedPan = panDeltaWithZoom - (targetOffsetWithinBounds.userOffset.value - targetOffset.userOffset.value)
    val isHorizontalPan = abs(panDeltaWithZoom.x) > abs(panDeltaWithZoom.y)

    return (if (isHorizontalPan) abs(consumedPan.x) else abs(consumedPan.y)) > ZoomDeltaEpsilon
  }

  /**
   * Translate this offset such that the visual position of [centroid]
   * remains the same after applying [panDelta] and [newZoom].
   */
  private fun AbsoluteOffset.retainCentroidPositionAfterZoom(
    centroid: Offset,
    panDelta: Offset = Offset.Zero,
    oldZoom: AbsoluteZoomFactor,
    newZoom: AbsoluteZoomFactor,
  ): AbsoluteOffset {
    check(this.isFinite) {
      "Can't center around an infinite offset ${collectDebugInfo()}"
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
    return transformUserOffset { currentOffset ->
      //
      // Move the centroid to the center
      //      of panned content(?)
      //                 |                           Scale
      //                 |                             |                Move back
      //                 |                             |           (+ new translation)
      //                 |                             |                    |
      // ________________|_______________      ________|_________   ________|_________
      ((currentOffset + centroid / oldZoom) - (centroid / newZoom + panDelta / oldZoom)).also {
        check(it.isFinite) {
          val debugInfo = collectDebugInfo(
            "centroid" to centroid,
            "panDelta" to panDelta,
            "oldZoom" to oldZoom,
            "newZoom" to newZoom,
          )
          "retainCentroidPositionAfterZoom() generated an infinite value. $debugInfo"
        }
      }
    }
  }

  private fun AbsoluteOffset.coerceWithinContentBounds(
    proposedZoom: AbsoluteZoomFactor,
    inputs: GestureStateInputs,
  ): AbsoluteOffset {
    check(isFinite) {
      "Can't coerce an infinite offset ${collectDebugInfo("proposedZoom" to proposedZoom)}"
    }

    val unscaledContentBounds = inputs.unscaledContentBounds
    val scaledTopLeft = unscaledContentBounds.topLeft * proposedZoom

    // Note to self: (-offset * zoom) is the final value used for displaying the content composable.
    return transformUserOffset { finalOffset ->
      finalOffset.withZoomAndTranslate(zoom = -proposedZoom.finalZoom(), translate = scaledTopLeft) {
        val expectedDrawRegion = Rect(it, unscaledContentBounds.size * proposedZoom).coerceAtMostMaxValue()
        expectedDrawRegion.calculateTopLeftToOverlapWith(
          viewportBounds = inputs.paddedViewportBounds,
          alignment = inputs.contentAlignment,
          layoutDirection = inputs.layoutDirection,
        )
      }
    }
  }

  private fun Rect.coerceAtMostMaxValue(): Rect {
    return if (size.isSpecified) {
      this
    } else {
      Rect(topLeft, Size(Float.MAX_VALUE, Float.MAX_VALUE)).also {}
    }
  }

  override fun setContentLocation(location: ZoomableContentLocation) {
    unscaledContentLocation = location
  }

  override suspend fun resetZoom(animationSpec: AnimationSpec<Float>) {
    awaitUntilIsReadyForInteraction()
    zoomTo(
      zoomFactor = zoomSpec.range.minZoomFactor(currentGestureStateInputs!!.baseZoom),
      animationSpec = animationSpec,
    )
  }

  override suspend fun zoomBy(
    zoomFactor: Float,
    focal: ZoomFocalPoint,
    animationSpec: AnimationSpec<Float>
  ) {
    awaitUntilIsReadyForInteraction()

    val gestureStateInputs = currentGestureStateInputs!!
    val gestureState = gestureState.calculate(gestureStateInputs)
    val currentZoom = AbsoluteZoomFactor(gestureStateInputs.baseZoom, gestureState.userZoom)
    val targetZoom = currentZoom.finalZoom().maxScale * zoomFactor

    zoomTo(
      zoomFactor = targetZoom,
      focal = focal,
      animationSpec = animationSpec,
    )
  }

  override suspend fun zoomTo(
    zoomFactor: Float,
    focal: ZoomFocalPoint,
    animationSpec: AnimationSpec<Float>,
  ) {
    if (zoomFactor <= 0) return
    awaitUntilIsReadyForInteraction()

    val targetZoom = SpatialScaleFactor(zoomFactor, CoordinateSpace.ZoomableContent)
      .coerceUserZoomIn(zoomSpec.range)  // Prevent overzooms. This doesn't support OverzoomEffect yet.

    val centroid = focal.computeCentroid(this, zoomFactor)
    val centroidInViewport = with(coordinateSystem) {
      centroid
        .takeOrElse { SpatialOffset(viewportSize.center, CoordinateSpace.Viewport) }
        .offsetIn(CoordinateSpace.Viewport)
    }
    animateZoomTo(
      targetZoom = targetZoom,
      centroid = centroidInViewport,
      mutatePriority = MutatePriority.UserInput,
      animationSpec = animationSpec,
    )
  }

  override suspend fun panBy(offset: SpatialOffset, animationSpec: AnimationSpec<Offset>) {
    awaitUntilIsReadyForInteraction()

    transformableState.transform(MutatePriority.UserInput) {
      var previous = Offset.Zero
      AnimationState(
        typeConverter = Offset.VectorConverter,
        initialValue = Offset.Zero,
      ).animateTo(
        targetValue = with(coordinateSystem) {
          offset.offsetIn(CoordinateSpace.Viewport)
        },
        animationSpec = animationSpec,
      ) {
        transformBy(panChange = this.value - previous)
        previous = this.value
      }
    }
  }

  private suspend fun animateZoomTo(
    targetZoom: SpatialScaleFactor,
    centroid: Offset,
    mutatePriority: MutatePriority,
    animationSpec: AnimationSpec<Float>,
  ) {
    awaitUntilIsReadyForInteraction()
    val gestureStateInputs = currentGestureStateInputs!!
    val startGestureState = gestureState.calculate(gestureStateInputs)

//    val startZoom = AbsoluteZoomFactor(gestureStateInputs.baseZoom, startGestureState.userZoom)
    val startOffset = AbsoluteOffset(gestureStateInputs.baseOffset, startGestureState.userOffset)

    val startZoom = startGestureState.userZoom.value

//    val panDelta = if (startZoom.userZoom.value == targetZoom.userZoom.value) {
//      // When zoom doesn't change, calculate the pan needed to center the content around the centroid.
//      gestureStateInputs.viewportSize.center - centroid
//    } else {
//      Offset.Zero
//    }

    val targetOffset = startOffset
//      .retainCentroidPositionAfterZoom(
//        centroid = centroid,
//        //panDelta = panDelta,
//        oldZoom = startZoom,
//        newZoom = targetZoom,
//      )
//      .coerceWithinContentBounds(
//        proposedZoom = targetZoom,
//        inputs = gestureStateInputs,
//      )

    val targetZoomInViewport = with(coordinateSystem) {
      targetZoom.scaleIn(CoordinateSpace.Viewport)
    }

    gestureState = GestureStateCalculator {
      startGestureState.copy(
        userOffset = targetOffset.userOffset,
        userZoom = UserZoomFactor(targetZoomInViewport.maxScale),
        lastCentroid = centroid,
      )
    }

//    transformableState.animatedTransform(mutatePriority) {
//      AnimationState(initialValue = 0f).animateTo(
//        targetValue = 1f,
//        animationSpec = animationSpec.withMinimalVisibilityThreshold(),
//      ) {
//        val animatedZoom: AbsoluteZoomFactor = startZoom.copy(
//          userZoom = UserZoomFactor(
//            lerp(
//              start = startZoom.userZoom.value,
//              stop = targetZoom.userZoom.value,
//              fraction = value
//            )
//          )
//        )
//        // For animating the offset, it is necessary to interpolate between values that the UI
//        // will see (i.e., -offset * zoom). Otherwise, a curve animation is produced if only the
//        // offset is used because the zoom and the offset values animate at different scales.
//        val animatedOffsetForUi = startOffset.copy(
//          userOffset = UserOffset(
//            -lerp(
//              start = (-startGestureState.userOffset.value * startZoom),
//              stop = (-targetOffset.userOffset.value * targetZoom),
//              fraction = value,
//            ) / animatedZoom
//          )
//        )
//        // Note to self: skipping transformableState#transformBy(), since it enforces offset-locking.
//        gestureState = GestureStateCalculator {
//          startGestureState.copy(
//            userOffset = animatedOffsetForUi.userOffset,
//            userZoom = animatedZoom.userZoom,
//            lastCentroid = centroid,
//          )
//        }
//      }
//    }
  }

  internal fun overzoomBoundaryState(): OverzoomBoundaryState {
    val gestureStateInputs = currentGestureStateInputs ?: return OverzoomBoundaryState.WithinBounds
    val gestureState = gestureState.calculate(gestureStateInputs)

    with(coordinateSystem) {
      val currentZoom = gestureState.userZoom.value.scaleIn(CoordinateSpace.Viewport)
      val maxZoom = zoomSpec.range.max().scaleIn(CoordinateSpace.Viewport)
      val minZoom = zoomSpec.range.min().scaleIn(CoordinateSpace.Viewport)

      return when {
        currentZoom.maxScale > maxZoom.maxScale -> OverzoomBoundaryState.OverZoomed
        currentZoom.maxScale < minZoom.maxScale -> OverzoomBoundaryState.UnderZoomed
        else -> OverzoomBoundaryState.WithinBounds
      }
    }
  }

  internal sealed class OverzoomBoundaryState {
    val isUnderOrOverZoomed: Boolean get() = this is UnderZoomed || this is OverZoomed

    data object OverZoomed : OverzoomBoundaryState()
    data object UnderZoomed : OverzoomBoundaryState()
    data object WithinBounds : OverzoomBoundaryState()
  }

  internal suspend fun animateSettlingOfZoomOnGestureEnd() {
    val gestureStateInputs = currentGestureStateInputs ?: error("shouldn't have gotten called")
    val gestureState = gestureState.calculate(gestureStateInputs)

    val userZoomWithinBounds = gestureState.userZoom.value
      .coerceUserZoomIn(zoomSpec.range)

    this.gestureState = GestureStateCalculator {
      gestureState.copy(
        userZoom = UserZoomFactor(userZoomWithinBounds),
      )
    }

//    transformableState.animatedTransform(MutatePriority.Default) {
//      AnimationState(initialValue = gestureState.userZoom.value).animateTo(
//        targetValue = userZoomWithinBounds.value,
//        animationSpec = ZoomableState.DefaultSettleAnimationSpec.withMinimalVisibilityThreshold(),
//      ) {
//        val current = calculateGestureState()!!.userZoom.value
//        transformBy(
//          centroid = gestureState.lastCentroid,
//          zoomChange = if (current == 0f) 1f else value / current,
//        )
//      }
//    }
  }

  internal suspend fun fling(velocity: Velocity, density: Density) {
    check(velocity.x.isFinite() && velocity.y.isFinite()) { "Invalid velocity = $velocity" }

    val gestureState = calculateGestureState() ?: error("called too early?")
    transformableState.animatedTransform(MutatePriorities.FlingAnimation) {
      var previous = gestureState.userOffset.value
      AnimationState(
        typeConverter = Offset.VectorConverter,
        initialValue = previous,
        initialVelocityVector = AnimationVector(velocity.x, velocity.y)
      ).animateDecay(splineBasedDecay(density)) {
        transformBy(
          centroid = gestureState.lastCentroid,
          panChange = (value - previous).also {
            check(it.isFinite) {
              val debugInfo = collectDebugInfo(
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

  @Composable
  fun RetainPanAcrossContentSizeChangesEffect() {
    LaunchedEffect(this) {
      withContext(Dispatchers.Main.immediate) { // To avoid flickers.
        snapshotFlow { currentGestureStateInputs }
          .mapNotNull { it?.unscaledContentBounds?.size }
          .zipWithPrevious(::Pair)
          .filter { (previous, current) ->
            abs(current.aspectRatio() - previous.aspectRatio()) < ZoomDeltaEpsilon
          }
          .collect { (previous, current) ->
            val scale = ScaleFactor(
              scaleX = current.width / previous.width,
              scaleY = current.height / previous.height,
            )
            // This unfortunately cancels any ongoing zoom/pan animations. It would be excellent
            // to support updating the offset without interrupting animations in the future.
            val currentGestureState = calculateGestureState()!!
            transformableState.transform(MutatePriority.PreventUserInput) {
              gestureState = GestureStateCalculator {
                currentGestureState.copy(
                  userOffset = currentGestureState.userOffset * scale
                )
              }
            }
          }
      }
    }
  }

  private fun calculateGestureState(): GestureState? {
    return currentGestureStateInputs?.let(gestureState::calculate)
  }

  private suspend fun TransformableState.animatedTransform(
    transformPriority: MutatePriority = MutatePriority.Default,
    block: suspend TransformScope.() -> Unit,
  ) {
    transform(transformPriority) {
      try {
        isAnimationRunning = true
        block()
      } finally {
        isAnimationRunning = false
      }
    }
  }

  private fun AnimationSpec<Float>.withMinimalVisibilityThreshold(): AnimationSpec<Float> {
    return if (this is SpringSpec<Float>) {
      // Without a low visibility threshold, spring() makes a huge
      // jump on its last frame causing a few frames to be dropped.
      copy(visibilityThreshold = 0.0001f)
    } else {
      this
    }
  }

  private fun collectDebugInfo(
    vararg extras: Pair<String, Any>,
    gestureState: GestureState? = null,
  ): String {
    fun readSafely(block: () -> Any?): String? {
      return try {
        block().toString()
      } catch (e: Throwable) {
        "(failed to read due to: $e)"
      }
    }

    return buildString {
      appendLine()
      extras.forEach { (key, value) ->
        appendLine("$key = $value")
      }
      appendLine("gestureStateInputs = ${readSafely { currentGestureStateInputs }}")
      appendLine("gestureState = ${gestureState ?: readSafely { calculateGestureState() }}")
      appendLine("contentTransformation = ${readSafely { contentTransformation }}")
      appendLine("contentScale = $contentScale")
      appendLine("unscaledContentLocation = $unscaledContentLocation")
      appendLine("zoomSpec = $zoomSpec")
      appendLine("Please share this error message on https://github.com/saket/telephoto/issues/new?")
    }
  }

  // Note to self: these bounds are in the viewport's coordinate space.
  internal inline fun transformUnscaledContentBoundsBy(
    clipToViewport: Boolean,
    transform: Rect.(GestureStateInputs, ZoomableContentTransformation) -> Rect
  ): Rect? {
    return with(contentTransformation) {
      val bounds = currentGestureStateInputs?.let { inputs ->
        inputs.unscaledContentBounds.withOrigin(transformOrigin) {
          val transformed = transform(inputs, this@with)
          if (clipToViewport) {
            transformed.intersect(Offset.Zero, inputs.viewportSize)
          } else {
            transformed
          }
        }
      }
      bounds
      // The placeholder bounds are always unscaled because
      // placeholders can't be zoomed (at least not yet).
        ?: placeholderBoundsProvider?.calculate()
    }
  }

  fun SpatialScaleFactor.coerceUserZoomIn(
    range: ZoomRange,
    leewayPercentForMinZoom: Float = 0f,
    leewayPercentForMaxZoom: Float = leewayPercentForMinZoom,
  ): SpatialScaleFactor {
    val space = this.space
    with(coordinateSystem) {
      val min = range.min().scaleIn(space)
      val max = range.max().scaleIn(space)
      val current = this@coerceUserZoomIn.scaleIn(space)
      return SpatialScaleFactor(
        scaleFactor = current.coerceIn(
          minimumValue = min * (1f - leewayPercentForMinZoom),
          maximumValue = max * (1f + leewayPercentForMaxZoom),
        ),
        space = space,
      )
    }
  }

  companion object {
    internal val Saver = Saver(
      save = { state ->
        SavedZoomableState(
          gestureState = SavedGestureState.from(state),
          autoApplyTransformations = state.autoApplyTransformations,
        )
      },
      restore = ::RealZoomableState,
    )
  }
}

/** An intermediate, non-normalized model used for generating [ZoomableContentTransformation]. */
internal data class GestureState(
  val userOffset: UserOffset,
  // Note to self: Having ContentZoomFactor here would be convenient, but it complicates
  // state restoration. This class should not capture any layout-related values.
  val userZoom: UserZoomFactor,
  // Centroid in the viewport (and not the unscaled content bounds).
  val lastCentroid: Offset,
)

internal data class GestureStateInputs(
  val viewportSize: Size,
  val paddedViewportBounds: Rect,
  val baseZoom: BaseZoomFactor,
  val baseOffset: Offset,
  val unscaledContentBounds: Rect,
  val contentAlignment: Alignment,
  val layoutDirection: LayoutDirection,
  val zoomSpec: ZoomSpec,
)

@Immutable
internal fun interface GestureStateCalculator {
  fun calculate(inputs: GestureStateInputs): GestureState
}

@Immutable
private fun interface GestureStateInputsCalculator {
  fun calculate(viewportSize: Size): GestureStateInputs?
}

/**
 * The minimum scale needed to position the content within its layout
 * bounds with respect to [ZoomableState.contentScale].
 **/
@JvmInline
@Immutable
internal value class BaseZoomFactor(val value: ScaleFactor) {
  val maxScale: Float get() = value.maxScale
}

/** Zoom applied by the user on top of [BaseZoomFactor]. */
@JvmInline
@Immutable
@Deprecated("replaced with SpatialScaleFactor")
internal value class UserZoomFactor(val value: SpatialScaleFactor) {
  constructor(factor: Float) : this(SpatialScaleFactor(factor, CoordinateSpace.Viewport))
}

@Deprecated("replaced with SpatialScaleFactor")
internal data class AbsoluteZoomFactor(
  private val baseZoom: BaseZoomFactor,
  val userZoom: UserZoomFactor,
) {
  fun finalZoom(): ScaleFactor = baseZoom * userZoom
//  private fun finalMaxScale(): Float = finalZoom().maxScale

  fun coerceUserZoomIn(
    range: ZoomRange,
    leewayPercentForMinZoom: Float = 0f,
    leewayPercentForMaxZoom: Float = leewayPercentForMinZoom,
  ): AbsoluteZoomFactor {
//    val minUserZoom = minimum(baseZoom, range).userZoom
//    val maxUserZoom = maximum(baseZoom, range).userZoom
//    return copy(
//      userZoom = UserZoomFactor(
//        userZoom.value.coerceIn(
//          minimumValue = minUserZoom.value * (1 - leewayPercentForMinZoom),
//          maximumValue = maxUserZoom.value * (1 + leewayPercentForMaxZoom),
//        )
//      )
//    )
    return this
  }

//  fun isAtMinZoom(range: ZoomRange): Boolean {
//    return (finalMaxScale() - minimum(baseZoom, range).finalMaxScale()) < ZoomDeltaEpsilon
//  }
//
//  fun isAtMaxZoom(range: ZoomRange): Boolean {
//    return (maximum(baseZoom, range).finalMaxScale() - finalMaxScale()) < ZoomDeltaEpsilon
//  }

  companion object {

    fun UserZoomFactor.isAtMinZoom(
      coordinateSystem: CoordinateSystem,
      range: ZoomRange,
    ): Boolean {
      with(coordinateSystem) {
        val min = range.min().scaleIn(CoordinateSpace.Viewport)
        val current = this@isAtMinZoom.value.scaleIn(CoordinateSpace.Viewport)
        return (current.maxScale - min.maxScale) < ZoomDeltaEpsilon
      }
    }

    fun UserZoomFactor.isAtMaxZoom(
      coordinateSystem: CoordinateSystem,
      range: ZoomRange,
    ): Boolean {
      with(coordinateSystem) {
        val max = range.max().scaleIn(CoordinateSpace.Viewport)
        val current = this@isAtMaxZoom.value.scaleIn(CoordinateSpace.Viewport)
        return (max.maxScale - current.maxScale) < ZoomDeltaEpsilon
      }
    }

    // todo: inline
    fun minimum(range: ZoomRange): SpatialScaleFactor {
      return range.min()
    }

    // todo: inline
    fun maximum(range: ZoomRange): SpatialScaleFactor {
      return range.max()
    }

    fun minimum(baseZoom: BaseZoomFactor, range: ZoomRange): AbsoluteZoomFactor {
      return AbsoluteZoomFactor(
        baseZoom = baseZoom,
        userZoom = UserZoomFactor(range.minZoomFactor(baseZoom)),
      )
    }

    fun maximum(baseZoom: BaseZoomFactor, range: ZoomRange): AbsoluteZoomFactor {
      return AbsoluteZoomFactor(
        baseZoom = baseZoom,
        userZoom = UserZoomFactor(range.maxZoomFactor(baseZoom) / baseZoom.maxScale),
      )
    }

    fun forFinalZoom(baseZoom: BaseZoomFactor, finalZoom: Float): SpatialScaleFactor {
      return SpatialScaleFactor(
        scaleFactor = finalZoom,
        space = CoordinateSpace.ZoomableContent,
      )
//      return AbsoluteZoomFactor(
//        baseZoom = baseZoom,
//        userZoom = UserZoomFactor(finalZoom / baseZoom.value.maxScale),
//      )
    }

    fun forFinalZoom(baseZoom: BaseZoomFactor, finalZoom: ScaleFactor): AbsoluteZoomFactor {
      return AbsoluteZoomFactor(
        baseZoom = baseZoom,
        userZoom = UserZoomFactor(finalZoom.maxScale / baseZoom.value.maxScale),
      )
    }
  }
}

/** Differences below this value are ignored when comparing two zoom values. */
internal const val ZoomDeltaEpsilon = 0.001f

/** Offset applied by the user on top of a base offset. Similar to [UserZoomFactor]. */
@JvmInline
@Immutable
internal value class UserOffset private constructor(val value: Offset) {
  companion object {
    operator fun invoke(value: Offset): UserOffset {
      val isZero = abs(value.x) == 0f && abs(value.y) == 0f // Negative zeroes lead to subtle calculations errors.
      return UserOffset(if (isZero) Offset.Zero else value)
    }
  }

  operator fun minus(other: Offset): UserOffset =
    UserOffset(value.minus(other))

  operator fun times(factor: ScaleFactor): UserOffset =
    UserOffset(value.times(factor))
}

internal data class AbsoluteOffset(
  /**
   * The minimum offset needed to position the content within its layout
   * bounds with respect to [ZoomableState.contentAlignment].
   * */
  private val baseOffset: Offset,
  val userOffset: UserOffset,
) {
  val isFinite: Boolean get() = finalOffset().isFinite

  fun finalOffset(): Offset = baseOffset + userOffset.value

  fun transformUserOffset(block: (finalOffset: Offset) -> Offset): AbsoluteOffset {
    val transformed = block(finalOffset())
    return this.copy(
      userOffset = UserOffset(transformed - this.baseOffset)
    )
  }

  companion object {
    fun forFinalOffset(baseOffset: Offset, finalOffset: Offset): AbsoluteOffset {
      return AbsoluteOffset(
        baseOffset = baseOffset,
        userOffset = UserOffset(finalOffset - baseOffset),
      )
    }
  }
}

internal data class ZoomRange(
  private val minZoomAsRatioOfBaseZoom: Float,
  private val maxZoomAsRatioOfSize: Float,
) {

  fun min(): SpatialScaleFactor {
    return SpatialScaleFactor(
      scaleFactor = minZoomAsRatioOfBaseZoom,
      space = CoordinateSpace.Viewport,
    )
  }

  fun max(): SpatialScaleFactor {
    return SpatialScaleFactor(
      scaleFactor = maxZoomAsRatioOfSize,
      space = CoordinateSpace.ZoomableContent,
    )
  }

  fun minZoomFactor(baseZoom: BaseZoomFactor): Float {
    return minZoomAsRatioOfBaseZoom
  }

  fun maxZoomFactor(baseZoom: BaseZoomFactor): Float {
    // Note to self: the max zoom factor can be less than the min zoom
    // factor if the content is scaled-up by default. This can be tested
    // by setting contentScale = CenterCrop.
    return maxOf(maxZoomAsRatioOfSize, minZoomFactor(baseZoom))
  }
}

/** Called when the zoom has reached its max/min limit. */
private fun OverzoomEffect.adjust(zoomDelta: Float): Float {
  val isZoomingIn = zoomDelta > 1f
  return when (this) {
    OverzoomEffect.RubberBanding -> {
      when {
        isZoomingIn -> 1f + zoomDelta / 250f
        else -> 1f - zoomDelta / 250
      }
    }
    OverzoomEffect.Disabled -> 1f
    OverzoomEffect.NoLimits -> zoomDelta
    else -> error("unknown overzoom effect = $this")
  }
}
