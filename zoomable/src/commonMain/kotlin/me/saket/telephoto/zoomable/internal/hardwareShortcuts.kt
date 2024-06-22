package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.HardwareShortcutsSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.internal.HardwareShortcutDetector.ShortcutEvent

/** Responds to keyboard and mouse events to zoom and pan. */
internal data class HardwareShortcutsElement(
  private val state: ZoomableState,
  private val spec: HardwareShortcutsSpec,
) : ModifierNodeElement<HardwareShortcutsNode>() {

  override fun create(): HardwareShortcutsNode {
    return HardwareShortcutsNode(state, spec)
  }

  override fun update(node: HardwareShortcutsNode) {
    node.state = state
    node.spec = spec
  }
}

internal class HardwareShortcutsNode(
  var state: ZoomableState,
  var spec: HardwareShortcutsSpec,
) : Modifier.Node(), KeyInputModifierNode, PointerInputModifierNode {

  val canPan: () -> Boolean = {
    state.contentTransformation.scaleMetadata.userZoom > 1f
  }
  val onZoom: (factor: Float, centroid: Offset) -> Unit = { factor, centroid ->
    coroutineScope.launch {
      state.zoomBy(
        zoomFactor = factor,
        centroid = centroid,
        withAnimation = false,
      )
    }
  }
  val onPan: (delta: DpOffset) -> Unit = { delta ->
    coroutineScope.launch {
      state.panBy(
        offset = with(requireDensity()) {
          Offset(x = delta.x.toPx(), y = delta.y.toPx())
        },
        withAnimation = false,
      )
    }
  }

  override fun onKeyEvent(event: KeyEvent): Boolean {
    if (event.type == KeyEventType.KeyDown) {
      val shortcut = spec.detector.detectKey(event)
      shortcut?.let(::handleShortcut)
      return shortcut != null
    } else {
      return false
    }
  }

  override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
    if (
      pointerEvent.type == PointerEventType.Scroll
      && pass == PointerEventPass.Main
      && pointerEvent.changes.fastAny { !it.isConsumed }
    ) {
      val shortcut = spec.detector.detectScroll(pointerEvent)
      if (shortcut != null) {
        pointerEvent.changes.fastForEach { it.consume() }
        handleShortcut(shortcut)
      }
    }
  }

  private fun handleShortcut(shortcut: ShortcutEvent) {
    when (shortcut) {
      is ShortcutEvent.Zoom -> {
        when (shortcut.direction) {
          ShortcutEvent.ZoomDirection.In -> onZoom(shortcut.zoomFactor, shortcut.centroid)
          ShortcutEvent.ZoomDirection.Out -> onZoom(1f / shortcut.zoomFactor, shortcut.centroid)
        }
      }
      is ShortcutEvent.Pan -> {
        if (canPan()) {
          val offset = when (shortcut.direction) {
            ShortcutEvent.PanDirection.Up -> DpOffset(x = 0.dp, y = shortcut.panOffset)
            ShortcutEvent.PanDirection.Down -> DpOffset(x = 0.dp, y = -shortcut.panOffset)
            ShortcutEvent.PanDirection.Left -> DpOffset(x = shortcut.panOffset, y = 0.dp)
            ShortcutEvent.PanDirection.Right -> DpOffset(x = -shortcut.panOffset, y = 0.dp)
          }
          onPan(offset)
        }
      }
    }
  }

  override fun onPreKeyEvent(event: KeyEvent): Boolean = false
  override fun onCancelPointerInput() = Unit
}
