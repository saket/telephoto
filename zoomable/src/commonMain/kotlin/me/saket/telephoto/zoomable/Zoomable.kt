package me.saket.telephoto.zoomable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.internal.HardwareShortcutsElement
import me.saket.telephoto.zoomable.internal.MutatePriorities
import me.saket.telephoto.zoomable.internal.TappableAndQuickZoomableElement
import me.saket.telephoto.zoomable.internal.TransformableElement
import me.saket.telephoto.zoomable.internal.hapticFeedbackPerformer
import me.saket.telephoto.zoomable.internal.stopTransformation

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
 *
 * Because `Modifier.zoomable()` consumes all gestures including double-taps, [Modifier.clickable] and
 * [Modifier.combinedClickable] will not work on the composable this `Modifier.zoomable()` is applied to.
 * As an alternative, [onClick] and [onLongClick] parameters can be used instead.
 *
 * @param enabled whether or not gestures are enabled.
 *
 * @param clipToBounds defaults to true to act as a reminder that this layout should probably fill all
 * available space. Otherwise, gestures made outside the composable's layout bounds will not be registered.
 * */
@OptIn(ExperimentalTelephotoApi::class)
fun Modifier.zoomable(
  state: ZoomableState,
  enabled: Boolean = true,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
  onDoubleClick: DoubleClickToZoomListener? = DoubleClickToZoomListener.cycle(),
): Modifier {
  return this.zoomable(
    state = state,
    pinchToZoomEnabled = enabled,
    quickZoomEnabled = enabled,
    onClick = onClick?.let {
      { clickedAt ->
        val viewportOffset = with(state.coordinateSystem) {
          clickedAt.offsetIn(CoordinateSpace.Viewport)
        }
        onClick(viewportOffset)
      }
    },
    onLongClick = onLongClick?.let {
      { clickedAt ->
        val viewportOffset = with(state.coordinateSystem) {
          clickedAt.offsetIn(CoordinateSpace.Viewport)
        }
        onLongClick(viewportOffset)
      }
    },
    clipToBounds = clipToBounds,
    onDoubleClick = onDoubleClick,
  )
}

// todo: how do i make this public without causing an overload ambiguity?
/** See [Modifier.zoomable]. */
@ExperimentalTelephotoApi
private fun Modifier.zoomable2(
  state: ZoomableState,
  enabled: Boolean = true,
  onClick: (CoordinateSystem.(SpatialOffset) -> Unit)?,
  onLongClick: (CoordinateSystem.(SpatialOffset) -> Unit)?,
  clipToBounds: Boolean = true,
  onDoubleClick: DoubleClickToZoomListener? = DoubleClickToZoomListener.cycle(),
): Modifier {
  check(state is RealZoomableState)
  return this.zoomable(
    state = state,
    pinchToZoomEnabled = enabled,
    quickZoomEnabled = enabled,
    onClick = onClick,
    onLongClick = onLongClick,
    clipToBounds = clipToBounds,
    onDoubleClick = onDoubleClick,
  )
}

@OptIn(ExperimentalTelephotoApi::class)
private fun Modifier.zoomable(
  state: ZoomableState,
  pinchToZoomEnabled: Boolean = true,
  quickZoomEnabled: Boolean = true,
  onClick: (CoordinateSystem.(SpatialOffset) -> Unit)? = null,
  onLongClick: (CoordinateSystem.(SpatialOffset) -> Unit)? = null,
  clipToBounds: Boolean = true,
  onDoubleClick: DoubleClickToZoomListener? = DoubleClickToZoomListener.cycle(),
): Modifier {
  if (pinchToZoomEnabled && !quickZoomEnabled) {
    // Note to self: this function isn't public because it feels weird to
    // have click listeners that will only work if quickZoomEnabled is true.
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
        pinchToZoomEnabled = pinchToZoomEnabled,
        quickZoomEnabled = quickZoomEnabled,
        onClick = onClick,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
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

@OptIn(ExperimentalTelephotoApi::class)
internal fun Modifier.pinchToZoomable(
  state: ZoomableState,
  clipToBounds: Boolean = true,
): Modifier {
  return this.zoomable(
    state = state,
    pinchToZoomEnabled = true,
    quickZoomEnabled = false,
    onClick = null,
    onLongClick = null,
    onDoubleClick = null,
    clipToBounds = clipToBounds,
  )
}

@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
fun Modifier.zoomable(
  state: ZoomableState,
  enabled: Boolean = true,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
): Modifier {
  return this.zoomable(
    state = state,
    enabled = enabled,
    onClick = onClick,
    onLongClick = onLongClick,
    clipToBounds = clipToBounds,
    onDoubleClick = DoubleClickToZoomListener.cycle(),
  )
}

@OptIn(ExperimentalTelephotoApi::class)
private data class ZoomableElement(
  private val state: RealZoomableState,
  private val pinchToZoomEnabled: Boolean,
  private val quickZoomEnabled: Boolean,
  private val onClick: (CoordinateSystem.(SpatialOffset) -> Unit)?,
  private val onLongClick: (CoordinateSystem.(SpatialOffset) -> Unit)?,
  private val onDoubleClick: DoubleClickToZoomListener?,
) : ModifierNodeElement<ZoomableNode>() {

  override fun create(): ZoomableNode = ZoomableNode(
    state = state,
    pinchToZoomEnabled = pinchToZoomEnabled,
    quickZoomEnabled = quickZoomEnabled,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
  )

  override fun update(node: ZoomableNode) {
    node.update(
      state = state,
      pinchToZoomEnabled = pinchToZoomEnabled,
      quickZoomEnabled = quickZoomEnabled,
      onClick = onClick,
      onLongClick = onLongClick,
      onDoubleClick = onDoubleClick,
    )
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "zoomable"
    properties["state"] = state
    properties["pinchToZoomEnabled"] = pinchToZoomEnabled
    properties["quickZoomEnabled"] = quickZoomEnabled
    properties["onClick"] = onClick
    properties["onLongClick"] = onLongClick
    properties["onDoubleClick"] = onDoubleClick
  }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalTelephotoApi::class)
private class ZoomableNode(
  private var state: RealZoomableState,
  pinchToZoomEnabled: Boolean,
  quickZoomEnabled: Boolean,
  onClick: (CoordinateSystem.(SpatialOffset) -> Unit)?,
  onLongClick: (CoordinateSystem.(SpatialOffset) -> Unit)?,
  onDoubleClick: DoubleClickToZoomListener?,
) : DelegatingNode(), CompositionLocalConsumerModifierNode {

  private val hapticFeedback = hapticFeedbackPerformer()

  val onPress: () -> Unit = {
    coroutineScope.launch {
      state.transformableState.stopTransformation(MutatePriorities.FlingAnimation)
    }
  }
  val onQuickZoomStopped = {
    if (state.isZoomOutsideRange()) {
      coroutineScope.launch {
        hapticFeedback.performHapticFeedback()
        state.animateSettlingOfZoomOnGestureEnd()
      }
    }
  }
  val onTransformStopped: (velocity: Velocity) -> Unit = { velocity ->
    if (state.isReadyForInteraction) {
      coroutineScope.launch {
        if (state.isZoomOutsideRange()) {
          hapticFeedback.performHapticFeedback()
          state.animateSettlingOfZoomOnGestureEnd()
        } else {
          state.fling(velocity = velocity, density = requireDensity())
        }
      }
    }
  }

  private val tappableAndQuickZoomableNode = TappableAndQuickZoomableElement(
    quickZoomEnabled = quickZoomEnabled,
    transformableState = state.transformableState,
    onPress = onPress,
    onTap = onClick?.withCoordinateSystem(),
    onLongPress = onLongClick?.withCoordinateSystem(),
    onDoubleTap = onDoubleClick?.withCoroutineScope(),
    onQuickZoomStopped = onQuickZoomStopped,
  ).create()

  private val transformableNode = TransformableElement(
    state = state.transformableState,
    canPan = state::canConsumePanChange,
    enabled = pinchToZoomEnabled,
    onTransformStopped = onTransformStopped,
    lockRotationOnZoomPan = false,
  ).create()

  init {
    // Note to self: the order in which these nodes are delegated is important.
    delegate(tappableAndQuickZoomableNode)
    delegate(transformableNode)
  }

  fun update(
    state: RealZoomableState,
    pinchToZoomEnabled: Boolean,
    quickZoomEnabled: Boolean,
    onClick: (CoordinateSystem.(SpatialOffset) -> Unit)?,
    onLongClick: (CoordinateSystem.(SpatialOffset) -> Unit)?,
    onDoubleClick: DoubleClickToZoomListener?,
  ) {
    if (this.state != state) {
      // Note to self: when the state is updated, the delegated
      // nodes are implicitly reset in the following update() calls.
      this.state = state
    }
    transformableNode.update(
      state = state.transformableState,
      canPan = state::canConsumePanChange,
      lockRotationOnZoomPan = false,
      enabled = pinchToZoomEnabled,
      onTransformStopped = onTransformStopped,
    )
    tappableAndQuickZoomableNode.update(
      onPress = onPress,
      onTap = onClick?.withCoordinateSystem(),
      onLongPress = onLongClick?.withCoordinateSystem(),
      onDoubleTap = onDoubleClick?.withCoroutineScope(),
      onQuickZoomStopped = onQuickZoomStopped,
      transformableState = state.transformableState,
      quickZoomEnabled = quickZoomEnabled,
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
