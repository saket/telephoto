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
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.RealZoomableState

/** Responds to keyboard and mouse events to zoom and pan. */
internal data class HardwareShortcutsElement(
  private val state: RealZoomableState,
) : ModifierNodeElement<HardwareShortcutsNode>() {

  override fun create(): HardwareShortcutsNode {
    return HardwareShortcutsNode(state)
  }

  override fun update(node: HardwareShortcutsNode) {
    node.state = state
  }
}

internal class HardwareShortcutsNode(
  var state: RealZoomableState,
) : Modifier.Node(), KeyInputModifierNode, PointerInputModifierNode {

  val canPan: () -> Boolean = {
    state.canConsumeKeyboardPan()
  }
  val onZoom: (factor: Float, centroid: Offset) -> Unit = { factor, centroid ->
    coroutineScope.launch {
      state.zoomBy(factor, centroid)
    }
  }
  val onPan: (delta: DpOffset) -> Unit = { delta ->
    coroutineScope.launch {
      state.panBy(
        with(requireDensity()) {
          Offset(x = delta.x.toPx(), y = delta.y.toPx())
        }
      )
    }
  }

  override fun onKeyEvent(event: KeyEvent): Boolean {
    if (event.type == KeyEventType.KeyDown) {
      val shortcut = state.hardwareShortcutsSpec.detector.detectKey(event)
      shortcut?.let(::handleShortcut)
      return shortcut != null
    } else {
      return false
    }
  }

  override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
    if (pointerEvent.type == PointerEventType.Scroll && pass == PointerEventPass.Main) {
      val shortcut = state.hardwareShortcutsSpec.detector.detectScroll(pointerEvent)
      if (shortcut != null) {
        handleShortcut(shortcut)
      }
    }
  }

  private fun handleShortcut(shortcut: KeyboardShortcut) {
    // macOS keyboard shortcuts:
    // meta + plus: zoom in
    // meta + minus: zoom out
    // arrow keys: pan
    // alt + arrow keys: super pan
    // cmd + arrow keys: pan to end

    // Android shortcuts:
    // mouse wheel: zoom in/out
    // alt + arrow keys: super pan
    // ctrl + plus/minus: zoom in/out (google chrome)

    // todo: long pans are platform and app dependent.
    //  on macOS, chrome scrolls ~32x. macOS's preview app scrolls 10x.
    //  on android, chrome scrolls 17x.

    when (shortcut) {
      is KeyboardShortcut.Zoom -> {
        when (shortcut.direction) {
          KeyboardShortcut.ZoomDirection.In -> onZoom(shortcut.zoomFactor, shortcut.centroid)
          KeyboardShortcut.ZoomDirection.Out -> onZoom(1f / shortcut.zoomFactor, shortcut.centroid)
        }
      }
      is KeyboardShortcut.Pan -> {
        if (canPan()) {
          val offset = when (shortcut.direction) {
            KeyboardShortcut.PanDirection.Up -> DpOffset(x = 0.dp, y = shortcut.panOffset)
            KeyboardShortcut.PanDirection.Down -> DpOffset(x = 0.dp, y = -shortcut.panOffset)
            KeyboardShortcut.PanDirection.Left -> DpOffset(x = shortcut.panOffset, y = 0.dp)
            KeyboardShortcut.PanDirection.Right -> DpOffset(x = -shortcut.panOffset, y = 0.dp)
          }
          onPan(offset)
        }
      }
    }
  }

  override fun onPreKeyEvent(event: KeyEvent): Boolean = false
  override fun onCancelPointerInput() = Unit
}
