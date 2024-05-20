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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.internal.KeyboardActionsElement
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
 * - Double tap to zoom
 * - Single finger zoom (double tap and hold)
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
fun Modifier.zoomable(
  state: ZoomableState,
  enabled: Boolean = true,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
  enableKeyboardEvents: Boolean = false,  // todo: replace this with KeyboardShortcutsSpec
): Modifier {
  check(state is RealZoomableState)
  return this
    .thenIf(clipToBounds) {
      Modifier.clipToBounds()
    }
    .onSizeChanged { state.contentLayoutSize = it.toSize() }
    .then(
      ZoomableElement(
        state = state,
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick,
      )
    )
    .thenIf(enableKeyboardEvents) {
      // todo: maybe listen to keyboard events only if this is enabled?
      Modifier.focusable()
    }
    .thenIf(state.autoApplyTransformations) {
      Modifier.applyTransformation(state.contentTransformation)
    }
}

private data class ZoomableElement(
  private val state: RealZoomableState,
  private val enabled: Boolean,
  private val onClick: ((Offset) -> Unit)?,
  private val onLongClick: ((Offset) -> Unit)?,
) : ModifierNodeElement<ZoomableNode>() {

  override fun create(): ZoomableNode = ZoomableNode(
    state = state,
    enabled = enabled,
    onClick = onClick,
    onLongClick = onLongClick,
  )

  override fun update(node: ZoomableNode) {
    node.update(
      state = state,
      enabled = enabled,
      onClick = onClick,
      onLongClick = onLongClick,
    )
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "zoomable"
    properties["state"] = state
    properties["enabled"] = enabled
    properties["onClick"] = onClick
    properties["onLongClick"] = onLongClick
  }
}

@OptIn(ExperimentalFoundationApi::class)
private class ZoomableNode(
  private var state: RealZoomableState,
  enabled: Boolean,
  onClick: ((Offset) -> Unit)?,
  onLongClick: ((Offset) -> Unit)?,
) : DelegatingNode(), CompositionLocalConsumerModifierNode {

  private val hapticFeedback = hapticFeedbackPerformer()

  val onPress: (Offset) -> Unit = {
    coroutineScope.launch {
      state.transformableState.stopTransformation(MutatePriorities.FlingAnimation)
    }
  }
  val onDoubleTap: (centroid: Offset) -> Unit = { centroid ->
    coroutineScope.launch {
      state.handleDoubleTapZoomTo(centroid = centroid)
    }
  }
  val onQuickZoomStopped = {
    if (state.isZoomOutsideRange()) {
      coroutineScope.launch {
        hapticFeedback.performHapticFeedback()
        state.smoothlySettleZoomOnGestureEnd()
      }
    }
  }
  val onTransformStopped: (velocity: Velocity) -> Unit = { velocity ->
    coroutineScope.launch {
      if (state.isZoomOutsideRange()) {
        hapticFeedback.performHapticFeedback()
        state.smoothlySettleZoomOnGestureEnd()
      } else {
        state.fling(velocity = velocity, density = requireDensity())
      }
    }
  }
  val onKeyboardZoom: (Float) -> Unit = { factor ->
    coroutineScope.launch {
      state.animateZoomBy(factor)
    }
  }
  val onKeyboardPan: (DpOffset) -> Unit = { delta ->
    coroutineScope.launch {
      // todo: accept an animation spec for pans. some apps may not want to pan with animation?
      state.animatePanBy(
        with(requireDensity()) {
          Offset(x = delta.x.toPx(), y = delta.y.toPx())
        })
    }
  }

  private val tappableAndQuickZoomableNode = TappableAndQuickZoomableElement(
    gesturesEnabled = enabled,
    transformableState = state.transformableState,
    onPress = onPress,
    onTap = onClick,
    onLongPress = onLongClick,
    onDoubleTap = onDoubleTap,
    onQuickZoomStopped = onQuickZoomStopped,
  ).create()

  private val transformableNode = TransformableElement(
    state = state.transformableState,
    canPan = state::canConsumePanChange,
    enabled = enabled,
    onTransformStopped = onTransformStopped,
    lockRotationOnZoomPan = false,
  ).create()

  // todo: why not pass state.transformableState instead?
  private val keyboardActionsNode = KeyboardActionsElement(
    canPan = state::canConsumeKeyboardPan,
    onZoom = onKeyboardZoom,
    onPan = onKeyboardPan,
  ).create()

  init {
    // Note to self: the order in which these nodes are delegated is important.
    delegate(tappableAndQuickZoomableNode)
    delegate(transformableNode)
    delegate(keyboardActionsNode)
  }

  fun update(
    state: RealZoomableState,
    enabled: Boolean,
    onClick: ((Offset) -> Unit)?,
    onLongClick: ((Offset) -> Unit)?,
  ) {
    if (this.state != state) {
      // Note to self: when the state is updated, the delegated
      // nodes are implicitly reset in the following lines.
      this.state = state
    }
    transformableNode.update(
      state = state.transformableState,
      canPan = state::canConsumePanChange,
      lockRotationOnZoomPan = false,
      enabled = enabled,
      onTransformStopped = onTransformStopped,
    )
    tappableAndQuickZoomableNode.update(
      onPress = onPress,
      onTap = onClick,
      onLongPress = onLongClick,
      onDoubleTap = onDoubleTap,
      onQuickZoomStopped = onQuickZoomStopped,
      transformableState = state.transformableState,
      gesturesEnabled = enabled,
    )
    keyboardActionsNode.update(
      canPan = state::canConsumeKeyboardPan,
      onZoom = onKeyboardZoom,
      onPan = onKeyboardPan,
    )
  }
}

private inline fun Modifier.thenIf(predicate: Boolean, other: () -> Modifier): Modifier {
  return if (predicate) this.then(other()) else this
}
