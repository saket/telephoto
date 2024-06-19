package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
      state.animateZoomBy(factor, centroid)
    }
  }
  val onPan: (delta: DpOffset) -> Unit = { delta ->
    coroutineScope.launch {
      // todo: accept an animation spec for pans. some apps may not want to pan with animation?
      state.animatePanBy(
        with(requireDensity()) {
          Offset(x = delta.x.toPx(), y = delta.y.toPx())
        }
      )
    }
  }

  override fun onKeyEvent(event: KeyEvent): Boolean {
    return if (event.type == KeyEventType.KeyDown) {
      handleShortcut(
        state.hardwareShortcutsSpec.detector.detect(event)
      )
    } else {
      false
    }
  }

  override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
    handleShortcut(
      state.hardwareShortcutsSpec.detector.detect(pointerEvent)
    )
  }

  private fun handleShortcut(shortcut: KeyboardShortcut?): Boolean {
    // macOS keyboard shortcuts:
    // meta + plus: zoom in
    // meta + minus: zoom out
    // arrow keys: pan
    // alt + arrow keys: super pan
    // cmd + arrow keys: pan to end

    // Android shortcuts:
    // mouse wheel: zoom in/out
    // alt + arrow keys: super pan

    // todo: long pans are platform and app dependent.
    //  on macOS, chrome scrolls ~32x. macOS's preview app scrolls 10x.
    //  on android, chrome scrolls 17x.
    val zoomStep = 1.2f
    val panStep = 50.dp

    when (shortcut) {
      is KeyboardShortcut.Zoom -> {
        when (shortcut.direction) {
          KeyboardShortcut.ZoomDirection.In -> onZoom(zoomStep, Offset.Unspecified)
          KeyboardShortcut.ZoomDirection.Out -> onZoom(1f / zoomStep, Offset.Unspecified)
        }
        return true
      }
      is KeyboardShortcut.Pan -> {
        if (canPan()) {
          val multiplier = when (shortcut.type) {
            KeyboardShortcut.PanType.ShortPan -> 1f
            KeyboardShortcut.PanType.LongPan -> 17f // Copied from Chrome.
          }
          val multipliedStep = panStep * multiplier
          val offset = when (shortcut.direction) {
            KeyboardShortcut.PanDirection.Up -> DpOffset(x = 0.dp, y = -multipliedStep)
            KeyboardShortcut.PanDirection.Down -> DpOffset(x = 0.dp, y = multipliedStep)
            KeyboardShortcut.PanDirection.Left -> DpOffset(x = -multipliedStep, y = 0.dp)
            KeyboardShortcut.PanDirection.Right -> DpOffset(x = multipliedStep, y = 0.dp)
          }
          onPan(offset)
        }
        return true
      }
      null -> {
        return false
      }
    }
  }

  override fun onPreKeyEvent(event: KeyEvent): Boolean = false
  override fun onCancelPointerInput() = Unit
}
