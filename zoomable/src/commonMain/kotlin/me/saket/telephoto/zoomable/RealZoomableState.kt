@file:Suppress("ConstPropertyName")

package me.saket.telephoto.zoomable

import androidx.annotation.FloatRange
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.MutatePriority
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.times
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.util.lerp
import me.saket.telephoto.zoomable.ContentZoomFactor.Companion.ZoomDeltaEpsilon
import me.saket.telephoto.zoomable.ZoomableContentLocation.SameAsLayoutBounds
import me.saket.telephoto.zoomable.internal.MutatePriorities
import me.saket.telephoto.zoomable.internal.PlaceholderBoundsProvider
import me.saket.telephoto.zoomable.internal.RealZoomableContentTransformation
import me.saket.telephoto.zoomable.internal.TransformableState
import me.saket.telephoto.zoomable.internal.Zero
import me.saket.telephoto.zoomable.internal.ZoomableSavedState
import me.saket.telephoto.zoomable.internal.calculateTopLeftToOverlapWith
import me.saket.telephoto.zoomable.internal.coerceIn
import me.saket.telephoto.zoomable.internal.copy
import me.saket.telephoto.zoomable.internal.div
import me.saket.telephoto.zoomable.internal.isPositiveAndFinite
import me.saket.telephoto.zoomable.internal.isSpecifiedAndFinite
import me.saket.telephoto.zoomable.internal.maxScale
import me.saket.telephoto.zoomable.internal.minScale
import me.saket.telephoto.zoomable.internal.minus
import me.saket.telephoto.zoomable.internal.roundToIntSize
import me.saket.telephoto.zoomable.internal.times
import me.saket.telephoto.zoomable.internal.unaryMinus
import me.saket.telephoto.zoomable.internal.withOrigin
import me.saket.telephoto.zoomable.internal.withZoomAndTranslate
import kotlin.math.abs

@Stable
internal class RealZoomableState internal constructor(
  initialGestureState: GestureState? = null,
  autoApplyTransformations: Boolean = true,
  private val isLayoutPreview: Boolean = false, // todo: still needed?
) : ZoomableState {

  override val contentTransformation: ZoomableContentTransformation by derivedStateOf {
    val baseZoomFactor = baseZoomFactor2.provide(contentLayoutSize)
    val gestureState = gestureState2.provide(contentLayoutSize)

    println("creating contentTransformation. base-zoom = $baseZoomFactor, gesture state = $gestureState")

    if (gestureState != null && baseZoomFactor != null) {
      val contentZoom = ContentZoomFactor(baseZoomFactor, gestureState.userZoomFactor)
      RealZoomableContentTransformation(
        isSpecified = true,
        contentSize = gestureState.contentSize,
        scale = contentZoom.finalZoom(),
        scaleMetadata = RealZoomableContentTransformation.ScaleMetadata(
          initialScale = baseZoomFactor.value,
          userZoom = gestureState.userZoomFactor.value,
        ),
        offset = -gestureState.offset * contentZoom.finalZoom(),
        centroid = gestureState.lastCentroid,
      )
    } else {
      RealZoomableContentTransformation(
        isSpecified = false,
        contentSize = Size.Zero,
        scale = when {
          isLayoutPreview -> ScaleFactor(1f, 1f)
          else -> ScaleFactor.Zero  // Effectively hide the content until an initial zoom value is calculated.
        },
        scaleMetadata = RealZoomableContentTransformation.ScaleMetadata(
          initialScale = ScaleFactor.Zero,
          userZoom = 0f,
        ),
        offset = Offset.Zero,
        centroid = null,
      )
    }
  }

  override var autoApplyTransformations: Boolean by mutableStateOf(autoApplyTransformations)

  override var contentScale: ContentScale by mutableStateOf(ContentScale.Fit)

  override var contentAlignment: Alignment by mutableStateOf(Alignment.Center)

  @get:FloatRange(from = 0.0, to = 1.0)
  override val zoomFraction: Float? by derivedStateOf {
    val gestureState = gestureState2.provide(contentLayoutSize)
    val baseZoomFactor = baseZoomFactor2.provide(contentLayoutSize)
    if (gestureState != null && baseZoomFactor != null) {
      val min = ContentZoomFactor.minimum(baseZoomFactor, zoomSpec.range).userZoom
      val max = ContentZoomFactor.maximum(baseZoomFactor, zoomSpec.range).userZoom
      val current = gestureState.userZoomFactor.coerceIn(min, max)
      when {
        current == min && min == max -> 1f  // Content can't zoom.
        else -> ((current - min) / (max - min)).value.coerceIn(0f, 1f)
      }
    } else {
      null
    }
  }

  override var zoomSpec by mutableStateOf(ZoomSpec())

  private var gestureState2: GestureStateProvider by mutableStateOf(
    GestureStateProvider { contentLayoutSize ->
      initialGestureState ?: GestureState(
        offset = Offset.Zero,
        userZoomFactor = UserZoomFactor(1f),
        lastCentroid = contentLayoutSize.takeOrElse { Size.Zero }.center,
        contentSize = contentLayoutSize.takeOrElse { Size.Zero },
        isPlaceholder = true,
      )
    }
  )

  internal var hardwareShortcutsSpec by mutableStateOf(HardwareShortcutsSpec())
  internal var layoutDirection: LayoutDirection by mutableStateOf(LayoutDirection.Ltr)

  /**
   * Raw size of the zoomable content without any scaling applied.
   * Used to ensure that the content does not pan/zoom outside its limits.
   */
  private var unscaledContentLocation: ZoomableContentLocation by mutableStateOf(SameAsLayoutBounds)

  /**
   * Layout bounds of the zoomable content in the UI hierarchy, without any scaling applied.
   */
  internal var contentLayoutSize: Size by mutableStateOf(Size.Unspecified)

  private val unscaledContentBounds2: UnscaledContentBoundsProvider by derivedStateOf {
    UnscaledContentBoundsProvider { contentLayoutSize ->
      if (contentLayoutSize.isSpecified) {
        unscaledContentLocation.location(
          layoutSize = contentLayoutSize,
          direction = layoutDirection
        )
      } else {
        Rect.Zero
      }
    }
  }

  /** See [BaseZoomFactor]. */
  private val baseZoomFactor2: BaseZoomFactorProvider by derivedStateOf {
    BaseZoomFactorProvider { contentLayoutSize ->
      if (isReadyToInteract) {
        val unscaledContentBounds = unscaledContentBounds2.provide(contentLayoutSize)
        BaseZoomFactor(
          contentScale.computeScaleFactor(
            srcSize = unscaledContentBounds.size,
            dstSize = contentLayoutSize,
          )
        ).also {
          check(it.value != ScaleFactor.Zero) {
            "Base zoom shouldn't be zero. content bounds = $unscaledContentBounds, layout size = $contentLayoutSize"
          }
        }
      } else {
        null
      }
    }
  }

  /** See [PlaceholderBoundsProvider]. */
  internal var placeholderBoundsProvider: PlaceholderBoundsProvider? by mutableStateOf(null)

  override val transformedContentBounds: Rect by derivedStateOf {
    with(contentTransformation) {
      if (isSpecified) {
        unscaledContentBounds2.provide(contentLayoutSize).withOrigin(transformOrigin) {
          times(scale).translate(offset)
        }
      } else {
        placeholderBoundsProvider?.calculate(state = this@RealZoomableState) ?: Rect.Zero
      }
    }
  }

  /**
   * Whether sufficient information is available about the content to start
   * listening to pan & zoom gestures.
   */
  internal val isReadyToInteract: Boolean by derivedStateOf {
    contentLayoutSize.isSpecified && contentLayoutSize.minDimension > 0f // Prevent division by zero errors.
      && unscaledContentLocation.size(contentLayoutSize).let { it.isSpecified && it.minDimension > 0f }
  }

  @Suppress("NAME_SHADOWING")
  internal val transformableState = TransformableState { zoomDelta, panDelta, _, centroid ->
    check(panDelta.isSpecifiedAndFinite() && zoomDelta.isFinite() && centroid.isSpecifiedAndFinite()) {
      "Can't transform with zoomDelta=$zoomDelta, panDelta=$panDelta, centroid=$centroid. ${collectDebugInfoForIssue41()}"
    }

    if (!isReadyToInteract) {
      return@TransformableState
    }
    val lastGestureState: GestureState? = gestureState2.provide(contentLayoutSize)

    gestureState2 = GestureStateProvider { contentLayoutSize -> // todo: could this also react to content alignment and scale changes?
      val baseZoomFactor = baseZoomFactor2.provide(contentLayoutSize)!!
      val unscaledContentBounds = unscaledContentBounds2.provide(contentLayoutSize)

      val oldZoom = ContentZoomFactor(
        baseZoom = baseZoomFactor,
        userZoom = lastGestureState?.userZoomFactor ?: UserZoomFactor(1f),
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
      val newZoom = ContentZoomFactor(
        baseZoom = baseZoomFactor,
        userZoom = oldZoom.userZoom * zoomDelta,
      ).let {
        if (zoomSpec.preventOverOrUnderZoom && (isAtMinZoom || isAtMaxZoom)) {
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
        "New zoom is invalid/infinite = $newZoom. ${collectDebugInfoForIssue41("zoomDelta" to zoomDelta)}"
      }

      val oldOffset = lastGestureState.let {
        if (it != null && !it.isPlaceholder) {
          it.offset
        } else {
          val defaultAlignmentOffset = contentAlignment.align(
            size = (unscaledContentBounds.size * baseZoomFactor.value).roundToIntSize(),
            space = contentLayoutSize.roundToIntSize(),
            layoutDirection = layoutDirection
          )
          // Take the content's top-left into account because it may not start at 0,0.
          unscaledContentBounds.topLeft + (-defaultAlignmentOffset.toOffset() / oldZoom)
        }
      }

      GestureState(
        offset = oldOffset
          .retainCentroidPositionAfterZoom(
            centroid = centroid,
            panDelta = panDelta,
            oldZoom = oldZoom,
            newZoom = newZoom,
          )
          .coerceWithinBounds(proposedZoom = newZoom, unscaledContentBounds = unscaledContentBounds),
        userZoomFactor = newZoom.userZoom,
        lastCentroid = centroid,
        contentSize = unscaledContentLocation.size(contentLayoutSize),
        isPlaceholder = false,
      )
    }
  }

  internal fun canConsumePanChange(panDelta: Offset): Boolean {
    val baseZoomFactor = baseZoomFactor2.provide(contentLayoutSize) ?: return false // Content is probably not ready yet. Ignore this gesture.
    val current = gestureState2.provide(contentLayoutSize) ?: return false

    val currentZoom = ContentZoomFactor(baseZoomFactor, current.userZoomFactor)
    val panDeltaWithZoom = panDelta / currentZoom
    val newOffset = current.offset - panDeltaWithZoom
    check(newOffset.isFinite) {
      "Offset can't be infinite ${collectDebugInfoForIssue41("panDelta" to panDelta)}"
    }

    val newOffsetWithinBounds = newOffset.coerceWithinBounds(
      proposedZoom = currentZoom,
      unscaledContentBounds = unscaledContentBounds2.provide(contentLayoutSize),
    )
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
    oldZoom: ContentZoomFactor,
    newZoom: ContentZoomFactor,
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

  private fun Offset.coerceWithinBounds(proposedZoom: ContentZoomFactor, unscaledContentBounds: Rect): Offset {
    check(this.isFinite) {
      "Can't coerce an infinite offset ${collectDebugInfoForIssue41("proposedZoom" to proposedZoom)}"
    }

    val scaledTopLeft = unscaledContentBounds.topLeft * proposedZoom

    // Note to self: (-offset * zoom) is the final value used for displaying the content composable.
    return withZoomAndTranslate(zoom = -proposedZoom.finalZoom(), translate = scaledTopLeft) { offset ->
      val expectedDrawRegion = Rect(offset, unscaledContentBounds.size * proposedZoom).throwIfDrawRegionIsTooLarge()
      expectedDrawRegion.calculateTopLeftToOverlapWith(contentLayoutSize, contentAlignment, layoutDirection)
    }
  }

  private fun Rect.throwIfDrawRegionIsTooLarge(): Rect {
    return also {
      check(size.isSpecified) {
        "The zoomable content is too large to safely calculate its draw region. This can happen if you're using" +
          " an unusually large value for ZoomSpec#maxZoomFactor (for e.g., Float.MAX_VALUE). Please file an issue" +
          " on https://github.com/saket/telephoto/issues if you think this is a mistake."
      }
    }
  }

  override fun setContentLocation(location: ZoomableContentLocation) {
    unscaledContentLocation = location
  }

  override suspend fun resetZoom(animationSpec: AnimationSpec<Float>) {
    val baseZoomFactor = baseZoomFactor2.provide(contentLayoutSize) ?: return
    zoomTo(
      zoomFactor = baseZoomFactor.maxScale,
      animationSpec = animationSpec,
    )
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

  override suspend fun zoomBy(
    zoomFactor: Float,
    centroid: Offset,
    animationSpec: AnimationSpec<Float>,
  ) {
    // todo: it'd be nice if contentLayoutSize was a channel so that I could subscribe to its value here.
    // todo: this is possibly wrong. gestureState2 currently has a non-null initial value.
    val gestureState = gestureState2.provide(contentLayoutSize) ?: return
    zoomTo(
      zoomFactor = gestureState.userZoomFactor.value * zoomFactor,
      centroid = centroid,
      animationSpec = animationSpec,
    )
  }

  override suspend fun zoomTo(
    zoomFactor: Float,
    centroid: Offset,
    animationSpec: AnimationSpec<Float>,
  ) {
    val baseZoomFactor = baseZoomFactor2.provide(contentLayoutSize) ?: return
    val targetZoom = ContentZoomFactor.forFinalZoom(
      baseZoom = baseZoomFactor,
      finalZoom = zoomFactor,
    )
    animateZoomTo(
      targetZoom = targetZoom,
      centroid = centroid.takeOrElse { contentLayoutSize.center },
      mutatePriority = MutatePriority.UserInput,
      animationSpec = animationSpec,
    )

    // Reset the zoom if needed. An advantage of doing *after* accepting the requested zoom
    // versus limiting the requested zoom above is that repeated over-zoom events (from
    // the keyboard for example) will result in a nice rubber banding effect.
    if (zoomSpec.preventOverOrUnderZoom && isZoomOutsideRange()) {
      animateSettlingOfZoomOnGestureEnd()
    }
  }

  override suspend fun panBy(offset: Offset, animationSpec: AnimationSpec<Offset>) {
    transformableState.transform(MutatePriority.UserInput) {
      var previous = Offset.Zero
      AnimationState(
        typeConverter = Offset.VectorConverter,
        initialValue = Offset.Zero,
      ).animateTo(
        targetValue = offset,
        animationSpec = animationSpec,
      ) {
        transformBy(panChange = this.value - previous)
        previous = this.value
      }
    }
  }

  private suspend fun animateZoomTo(
    targetZoom: ContentZoomFactor,
    centroid: Offset,
    mutatePriority: MutatePriority,
    animationSpec: AnimationSpec<Float>,
  ) {
    val startGestureState = gestureState2.provide(contentLayoutSize) ?: return
    val baseZoomFactor = baseZoomFactor2.provide(contentLayoutSize) ?: return

    val startZoom = ContentZoomFactor(baseZoomFactor, startGestureState.userZoomFactor)
    val targetOffset = startGestureState.offset
      .retainCentroidPositionAfterZoom(
        centroid = centroid,
        oldZoom = startZoom,
        newZoom = targetZoom,
      )
      .coerceWithinBounds(
        proposedZoom = targetZoom,
        unscaledContentBounds = unscaledContentBounds2.provide(contentLayoutSize),
      )

    transformableState.transform(mutatePriority) {
      AnimationState(initialValue = 0f).animateTo(
        targetValue = 1f,
        animationSpec = if (animationSpec is SpringSpec<Float>) {
          // Without a low visibility threshold, spring() makes a huge
          // jump on its last frame causing a few frames to be dropped.
          animationSpec.copy(visibilityThreshold = 0.0001f)
        } else {
          animationSpec
        },
      ) {
        val animatedZoom: ContentZoomFactor = startZoom.copy(
          userZoom = UserZoomFactor(
            lerp(
              start = startZoom.userZoom.value,
              stop = targetZoom.userZoom.value,
              fraction = value
            )
          )
        )
        // For animating the offset, it is necessary to interpolate between values that the UI
        // will see (i.e., -offset * zoom). Otherwise, a curve animation is produced if only the
        // offset is used because the zoom and the offset values animate at different scales.
        val animatedOffsetForUi: Offset = lerp(
          start = (-startGestureState.offset * startZoom),
          stop = (-targetOffset * targetZoom),
          fraction = value
        )

        gestureState2 = GestureStateProvider {
          startGestureState.copy(
            offset = (-animatedOffsetForUi) / animatedZoom,
            userZoomFactor = animatedZoom.userZoom,
            lastCentroid = centroid,
          )
        }
      }
    }
  }

  internal fun isZoomOutsideRange(): Boolean {
    val baseZoomFactor = baseZoomFactor2.provide(contentLayoutSize) ?: return false
    val userZoomFactor = gestureState2.provide(contentLayoutSize)?.userZoomFactor ?: return false

    val currentZoom = ContentZoomFactor(baseZoomFactor, userZoomFactor)
    val zoomWithinBounds = currentZoom.coerceUserZoomIn(zoomSpec.range)
    return abs(currentZoom.userZoom.value - zoomWithinBounds.userZoom.value) > ZoomDeltaEpsilon
  }

  internal suspend fun animateSettlingOfZoomOnGestureEnd() {
    check(isReadyToInteract) { "shouldn't have gotten called" }
    val gestureState = gestureState2.provide(contentLayoutSize)!!
    val baseZoomFactor = baseZoomFactor2.provide(contentLayoutSize)!!
    val userZoomWithinBounds = ContentZoomFactor(baseZoomFactor, gestureState.userZoomFactor)
      .coerceUserZoomIn(zoomSpec.range)
      .userZoom

    transformableState.transform(MutatePriority.Default) {
      var previous = gestureState.userZoomFactor.value
      AnimationState(initialValue = previous).animateTo(
        targetValue = userZoomWithinBounds.value,
        animationSpec = spring()
      ) {
        transformBy(
          centroid = gestureState.lastCentroid,
          zoomChange = if (previous == 0f) 1f else value / previous,
        )
        previous = this.value
      }
    }
  }

  internal suspend fun fling(velocity: Velocity, density: Density) {
    check(velocity.x.isFinite() && velocity.y.isFinite()) { "Invalid velocity = $velocity" }

    val gestureState = gestureState2.provide(contentLayoutSize)!!
    transformableState.transform(MutatePriorities.FlingAnimation) {
      var previous = gestureState.offset
      AnimationState(
        typeConverter = Offset.VectorConverter,
        initialValue = previous,
        initialVelocityVector = AnimationVector(velocity.x, velocity.y)
      ).animateDecay(splineBasedDecay(density)) {
        transformBy(
          centroid = gestureState.lastCentroid,
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
      appendLine("gestureState = ${gestureState2.provide(contentLayoutSize)}")
      appendLine("contentTransformation = $contentTransformation")
      appendLine("contentScale = $contentScale")
      appendLine("contentAlignment = $contentAlignment")
      appendLine("isReadyToInteract = $isReadyToInteract")
      appendLine("unscaledContentLocation = $unscaledContentLocation")
      appendLine("unscaledContentBounds = ${unscaledContentBounds2.provide(contentLayoutSize)}")
      appendLine("contentLayoutSize = $contentLayoutSize")
      appendLine("zoomSpec = $zoomSpec")
      appendLine("Please share this error message to https://github.com/saket/telephoto/issues/41?")
    }
  }

  companion object {
    internal val Saver = Saver<RealZoomableState, ZoomableSavedState>(
      save = {
        // todo: good enough?
        val gestureState = if (it.isReadyToInteract) {
          it.gestureState2.provide(it.contentLayoutSize)
        } else {
          null
        }
        ZoomableSavedState(gestureState)
      },
      restore = { RealZoomableState(initialGestureState = it.asGestureState()) }
    )
  }
}

/** An intermediate, non-normalized model used for generating [ZoomableContentTransformation]. */
internal data class GestureState(
  val offset: Offset,
  val userZoomFactor: UserZoomFactor, // todo: rename to userZoom for consistency with ContentZoomFactor
  val lastCentroid: Offset,
  val contentSize: Size,  // todo: why should this be a part of GestureState?
  val isPlaceholder: Boolean, // todo: can this be avoided?
)

/**
 * The minimum scale needed to position the content within its layout
 * bounds with respect to [ZoomableState.contentScale].
 * */
@JvmInline
@Immutable
internal value class BaseZoomFactor(val value: ScaleFactor) {
  val maxScale: Float get() = value.maxScale
}

@Immutable
private fun interface BaseZoomFactorProvider {
  fun provide(contentLayoutSize: Size): BaseZoomFactor?
}

@Immutable
private fun interface GestureStateProvider {
  fun provide(contentLayoutSize: Size): GestureState? // todo: should this be nullable? if not, then how will consumers (e.g., zoomBy) know that the content is ready?
}

@Immutable
private fun interface UnscaledContentBoundsProvider {
  fun provide(contentLayoutSize: Size): Rect  // todo: should this be nullable?
}

/** Zoom applied by the user on top of [BaseZoomFactor]. */
@JvmInline
@Immutable
internal value class UserZoomFactor(val value: Float)

internal data class ContentZoomFactor(
  private val baseZoom: BaseZoomFactor,
  val userZoom: UserZoomFactor,
) {
  fun finalZoom(): ScaleFactor = baseZoom * userZoom
  private fun finalMaxScale(): Float = finalZoom().maxScale

  fun coerceUserZoomIn(
    range: ZoomRange,
    leewayPercentForMinZoom: Float = 0f,
    leewayPercentForMaxZoom: Float = leewayPercentForMinZoom,
  ): ContentZoomFactor {
    val minUserZoom = minimum(baseZoom, range).userZoom
    val maxUserZoom = maximum(baseZoom, range).userZoom
    return copy(
      userZoom = UserZoomFactor(
        userZoom.value.coerceIn(
          minimumValue = minUserZoom.value * (1 - leewayPercentForMinZoom),
          maximumValue = maxUserZoom.value * (1 + leewayPercentForMaxZoom),
        )
      )
    )
  }

  fun isAtMinZoom(range: ZoomRange): Boolean {
    return (finalMaxScale() - minimum(baseZoom, range).finalMaxScale()) < ZoomDeltaEpsilon
  }

  fun isAtMaxZoom(range: ZoomRange): Boolean {
    return (maximum(baseZoom, range).finalMaxScale() - finalMaxScale()) < ZoomDeltaEpsilon
  }

  companion object {
    /** Differences below this value are ignored when comparing two zoom values. */
    const val ZoomDeltaEpsilon = 0.001f

    fun minimum(baseZoom: BaseZoomFactor, range: ZoomRange): ContentZoomFactor {
      return ContentZoomFactor(
        baseZoom = baseZoom,
        userZoom = UserZoomFactor(range.minZoomFactor(baseZoom) / baseZoom.maxScale),
      )
    }

    fun maximum(baseZoom: BaseZoomFactor, range: ZoomRange): ContentZoomFactor {
      return ContentZoomFactor(
        baseZoom = baseZoom,
        userZoom = UserZoomFactor(range.maxZoomFactor(baseZoom) / baseZoom.maxScale),
      )
    }

    fun forFinalZoom(baseZoom: BaseZoomFactor, finalZoom: Float): ContentZoomFactor {
      return ContentZoomFactor(
        baseZoom = baseZoom,
        userZoom = UserZoomFactor(finalZoom / baseZoom.maxScale),
      )
    }
  }
}

internal data class ZoomRange(
  private val minZoomAsRatioOfBaseZoom: Float = 1f,
  private val maxZoomAsRatioOfSize: Float,
) {

  fun minZoomFactor(baseZoom: BaseZoomFactor): Float {
    return minZoomAsRatioOfBaseZoom * baseZoom.maxScale
  }

  fun maxZoomFactor(baseZoom: BaseZoomFactor): Float {
    // Note to self: the max zoom factor can be less than the min zoom
    // factor if the content is scaled-up by default. This can be tested
    // by setting contentScale = CenterCrop.
    return maxOf(maxZoomAsRatioOfSize, minZoomFactor(baseZoom))
  }
}
