package me.saket.telephoto.zoomable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.launch
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.RealZoomableState.OverzoomBoundaryState
import me.saket.telephoto.zoomable.internal.HardwareShortcutsElement
import me.saket.telephoto.zoomable.internal.MutatePriorities
import me.saket.telephoto.zoomable.internal.PressInteractionElement
import me.saket.telephoto.zoomable.internal.TappableAndQuickZoomableElement
import me.saket.telephoto.zoomable.internal.TransformableElement
import me.saket.telephoto.zoomable.internal.stopTransformation
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.spatial.CoordinateSystem
import me.saket.telephoto.zoomable.spatial.SpatialOffset

/**
 * A `Modifier` for handling pan & zoom gestures, designed to be shared across all your media
 * composables so that your users can use the same familiar gestures throughout your app. It offers,
 *
 * - Pinch to zoom and flings
 * - Double click to zoom
 * - Single finger zoom (double click and hold)
 * - Haptic feedback for over/under zoom
 * - Compatibility with nested scrolling
 * - Click listeners
 * - Keyboard and mouse shortcuts
 * - State preservation across config changes (including screen rotations)
 *
 * Because `Modifier.zoomable()` consumes all gestures including double-taps, [Modifier.clickable] and
 * [Modifier.combinedClickable] will not work on the composable this modifier is applied to.
 * As an alternative, [onClick] and [onLongClick] parameters can be used instead.
 *
 * @param clipToBounds defaults to true to act as a reminder that this layout should probably fill all
 * available space. Otherwise, gestures made outside the composable's layout bounds will not be registered.
 * */
fun Modifier.zoomable(
  state: ZoomableState,
  gestures: EnabledZoomGestures,
  onClick: ((clickedAt: Offset) -> Unit)? = null,
  onLongClick: ((clickedAt: Offset) -> Unit)? = null,
  onDoubleClick: DoubleClickToZoomListener? = DoubleClickToZoomListener.cycle(),
  clipToBounds: Boolean = true,
  interactionSource: MutableInteractionSource? = null,
): Modifier {
  @OptIn(ExperimentalTelephotoApi::class)
  return this.zoomableInternal(
    state = state,
    gestures = gestures,
    onClick = onClick?.let {
      { clickedAt: SpatialOffset ->
        val viewportOffset = with(state.coordinateSystem) {
          clickedAt.offsetIn(CoordinateSpace.Viewport)
        }
        onClick(viewportOffset)
      }
    },
    onLongClick = onLongClick?.let {
      { clickedAt: SpatialOffset ->
        val viewportOffset = with(state.coordinateSystem) {
          clickedAt.offsetIn(CoordinateSpace.Viewport)
        }
        onLongClick(viewportOffset)
      }
    },
    clipToBounds = clipToBounds,
    onDoubleClick = onDoubleClick,
    interactionSource = interactionSource,
  )
}

fun Modifier.zoomable(
  state: ZoomableState,
  onClick: ((clickedAt: Offset) -> Unit)? = null,
  onLongClick: ((clickedAt: Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
  onDoubleClick: DoubleClickToZoomListener? = DoubleClickToZoomListener.cycle(),
): Modifier {
  return this.zoomable(
    state = state,
    gestures = EnabledZoomGestures.ZoomAndPan,
    onClick = onClick,
    onLongClick = onLongClick,
    clipToBounds = clipToBounds,
    onDoubleClick = onDoubleClick,
  )
}

@Deprecated(
  "Use the 'gestures' parameter instead. " +
    "Replace `enabled = true` with `gestures = ZoomInteractions.ZoomAndPan`, " +
    "or `enabled = false` with `gestures = ZoomInteractions.None`.",
)
fun Modifier.zoomable(
  state: ZoomableState,
  enabled: Boolean = true,
  onClick: ((clickedAt: Offset) -> Unit)? = null,
  onLongClick: ((clickedAt: Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
  onDoubleClick: DoubleClickToZoomListener? = DoubleClickToZoomListener.cycle(),
): Modifier {
  return this.zoomable(
    state = state,
    gestures = if (enabled) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
    onClick = onClick,
    onLongClick = onLongClick,
    clipToBounds = clipToBounds,
    onDoubleClick = onDoubleClick,
  )
}

@OptIn(ExperimentalTelephotoApi::class)
private fun Modifier.zoomableInternal(
  state: ZoomableState,
  gestures: EnabledZoomGestures,
  onClick: (CoordinateSystem.(SpatialOffset) -> Unit)? = null,
  onLongClick: (CoordinateSystem.(SpatialOffset) -> Unit)? = null,
  clipToBounds: Boolean = true,
  onDoubleClick: DoubleClickToZoomListener? = DoubleClickToZoomListener.cycle(),
  interactionSource: MutableInteractionSource? = null,
): Modifier {
  if (gestures.pinchToZoom && !gestures.quickZoom) {
    // Note to self: this function isn't public because it feels weird to
    // have click listeners that will only work when quick zoom is enabled.
    check(onClick == null)
    check(onLongClick == null)
  }

  check(state is RealZoomableState)
  return this
    .thenIf(clipToBounds) {
      Modifier.clipToBounds()
    }
    .onSizeChanged { state.viewportSize = it.toSize() }
    .then(
      ZoomableElement(
        state = state,
        gestures = gestures,
        onClick = onClick,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        interactionSource = interactionSource,
      )
    )
    .thenIf(state.hardwareShortcutsSpec.enabled) {
      Modifier
        .then(HardwareShortcutsElement(state, state.hardwareShortcutsSpec))
        .focusable()
    }
    .thenIf(state.autoApplyTransformations) {
      Modifier.applyTransformation { state.contentTransformation }
    }
}

@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
fun Modifier.zoomable(
  state: ZoomableState,
  enabled: Boolean = true,
  onClick: ((clickedAt: Offset) -> Unit)? = null,
  onLongClick: ((clickedAt: Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
): Modifier {
  return this.zoomable(
    state = state,
    gestures = if (enabled) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
    onClick = onClick,
    onLongClick = onLongClick,
    clipToBounds = clipToBounds,
    onDoubleClick = DoubleClickToZoomListener.cycle(),
  )
}

@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
fun Modifier.zoomable(
  state: ZoomableState,
  gestures: EnabledZoomGestures,
  onClick: ((clickedAt: Offset) -> Unit)? = null,
  onLongClick: ((clickedAt: Offset) -> Unit)? = null,
  onDoubleClick: DoubleClickToZoomListener? = DoubleClickToZoomListener.cycle(),
  clipToBounds: Boolean = true,
): Modifier {
  return this.zoomable(
    state = state,
    gestures = gestures,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
    interactionSource = null,
  )
}

@OptIn(ExperimentalTelephotoApi::class)
private data class ZoomableElement(
  private val state: RealZoomableState,
  private val gestures: EnabledZoomGestures,
  private val onClick: (CoordinateSystem.(SpatialOffset) -> Unit)?,
  private val onLongClick: (CoordinateSystem.(SpatialOffset) -> Unit)?,
  private val onDoubleClick: DoubleClickToZoomListener?,
  private val interactionSource: MutableInteractionSource?,
) : ModifierNodeElement<ZoomableNode>() {

  override fun create(): ZoomableNode = ZoomableNode(
    state = state,
    gestures = gestures,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    interactionSource = interactionSource,
  )

  override fun update(node: ZoomableNode) {
    node.update(
      state = state,
      gestures = gestures,
      onClick = onClick,
      onLongClick = onLongClick,
      onDoubleClick = onDoubleClick,
      interactionSource = interactionSource,
    )
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "zoomable"
    properties["state"] = state
    properties["gestures"] = gestures
    properties["onClick"] = onClick
    properties["onLongClick"] = onLongClick
    properties["onDoubleClick"] = onDoubleClick
    properties["interactionSource"] = interactionSource
  }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalTelephotoApi::class)
private class ZoomableNode(
  private var state: RealZoomableState,
  gestures: EnabledZoomGestures,
  onClick: (CoordinateSystem.(SpatialOffset) -> Unit)?,
  onLongClick: (CoordinateSystem.(SpatialOffset) -> Unit)?,
  onDoubleClick: DoubleClickToZoomListener?,
  interactionSource: MutableInteractionSource?,
) : DelegatingNode(), CompositionLocalConsumerModifierNode {

  private val hapticFeedback: HapticFeedback
    get() = currentValueOf(LocalHapticFeedback)

  val onPress: () -> Unit = {
    coroutineScope.launch {
      state.transformableState.stopTransformation(MutatePriorities.FlingAnimation)
    }
  }
  val onQuickZoomStopped = {
    if (state.overzoomBoundaryState().isUnderOrOverZoomed) {
      coroutineScope.launch {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Reject)
        state.animateSettlingOfZoomOnGestureEnd()
      }
    }
  }
  val onTransformStopped: (velocity: Velocity) -> Unit = { velocity ->
    if (state.isReadyForInteraction) {
      coroutineScope.launch {
        val boundaryState = state.overzoomBoundaryState()
        if (boundaryState is OverzoomBoundaryState.WithinBounds) {
          state.fling(velocity = velocity, density = requireDensity())
        } else {
          val hapticType = when (boundaryState) {
            OverzoomBoundaryState.OverZoomed -> state.zoomSpec.maximum.overzoomEffect.hapticFeedbackType()
            OverzoomBoundaryState.UnderZoomed -> state.zoomSpec.minimum.overzoomEffect.hapticFeedbackType()
            OverzoomBoundaryState.WithinBounds -> {
              // https://kotlinlang.org/docs/whatsnew2220.html#data-flow-based-exhaustiveness-checks-for-when-expressions
              error("will no longer be needed in a future kotlin release")
            }
          }
          hapticFeedback.performHapticFeedback(hapticType)
          state.animateSettlingOfZoomOnGestureEnd()
        }
      }
    }
  }

  private val tappableAndQuickZoomableNode = TappableAndQuickZoomableElement(
    quickZoomEnabled = gestures.quickZoom,
    transformableState = state.transformableState,
    onPress = onPress,
    onTap = onClick?.withCoordinateSystem(),
    onLongPress = onLongClick?.withCoordinateSystem(),
    onDoubleTap = onDoubleClick?.withCoroutineScope(),
    onQuickZoomStopped = onQuickZoomStopped,
  ).create()

  private val transformableNode = TransformableElement(
    state = state.transformableState,
    canPan = { gestures.pan && state.canConsumePanChange(it) },
    enabled = gestures.pinchToZoom,
    onTransformStopped = onTransformStopped,
    lockRotationOnZoomPan = false,
  ).create()

  private val pressInteractionNode = PressInteractionElement(
    interactionSource = interactionSource,
  ).create()

  init {
    // Note to self: the order in which these nodes are delegated is important.
    delegate(tappableAndQuickZoomableNode)
    delegate(transformableNode)
    delegate(pressInteractionNode)
  }

  fun update(
    state: RealZoomableState,
    gestures: EnabledZoomGestures,
    onClick: (CoordinateSystem.(SpatialOffset) -> Unit)?,
    onLongClick: (CoordinateSystem.(SpatialOffset) -> Unit)?,
    onDoubleClick: DoubleClickToZoomListener?,
    interactionSource: MutableInteractionSource?,
  ) {
    if (this.state != state) {
      // Note to self: when the state is updated, the delegated
      // nodes are implicitly reset in the following update() calls.
      this.state = state
    }
    transformableNode.update(
      state = state.transformableState,
      canPan = { gestures.pan && state.canConsumePanChange(it) },
      lockRotationOnZoomPan = false,
      enabled = gestures.pinchToZoom,
      onTransformStopped = onTransformStopped,
    )
    tappableAndQuickZoomableNode.update(
      onPress = onPress,
      onTap = onClick?.withCoordinateSystem(),
      onLongPress = onLongClick?.withCoordinateSystem(),
      onDoubleTap = onDoubleClick?.withCoroutineScope(),
      onQuickZoomStopped = onQuickZoomStopped,
      transformableState = state.transformableState,
      quickZoomEnabled = gestures.quickZoom,
    )
    pressInteractionNode.update(
      interactionSource = interactionSource,
    )
  }

  private fun (CoordinateSystem.(SpatialOffset) -> Unit).withCoordinateSystem(): (SpatialOffset) -> Unit {
    val delegate = this
    return { offset: SpatialOffset ->
      state.coordinateSystem.delegate(offset)
    }
  }

  private fun DoubleClickToZoomListener.withCoroutineScope(): (centroid: SpatialOffset) -> Unit {
    val delegate = this
    return { centroid: SpatialOffset ->
      coroutineScope.launch {
        with(delegate) {
          state.coordinateSystem.onDoubleClick(state, centroid)
        }
      }
    }
  }
}

private inline fun Modifier.thenIf(predicate: Boolean, other: () -> Modifier): Modifier {
  return if (predicate) this.then(other()) else this
}

private fun OverzoomEffect.hapticFeedbackType(): HapticFeedbackType {
  return when (this) {
    OverzoomEffect.NoLimits -> HapticFeedbackType.GestureEnd
    else -> HapticFeedbackType.Reject
  }
}
