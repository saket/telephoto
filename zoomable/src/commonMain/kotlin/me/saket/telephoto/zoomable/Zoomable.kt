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
    .thenIf(state.hardwareShortcutsSpec.enabled) {
      Modifier
        .focusable()
        .then(HardwareShortcutsElement(state, state.hardwareShortcutsSpec))
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
        state.animateSettlingOfZoomOnGestureEnd()
      }
    }
  }
  val onTransformStopped: (velocity: Velocity) -> Unit = { velocity ->
    coroutineScope.launch {
      if (state.isZoomOutsideRange()) {
        hapticFeedback.performHapticFeedback()
        state.animateSettlingOfZoomOnGestureEnd()
      } else {
        state.fling(velocity = velocity, density = requireDensity())
      }
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

  init {
    // Note to self: the order in which these nodes are delegated is important.
    delegate(tappableAndQuickZoomableNode)
    delegate(transformableNode)
  }

  fun update(
    state: RealZoomableState,
    enabled: Boolean,
    onClick: ((Offset) -> Unit)?,
    onLongClick: ((Offset) -> Unit)?,
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
  }
}

private inline fun Modifier.thenIf(predicate: Boolean, other: () -> Modifier): Modifier {
  return if (predicate) this.then(other()) else this
}
