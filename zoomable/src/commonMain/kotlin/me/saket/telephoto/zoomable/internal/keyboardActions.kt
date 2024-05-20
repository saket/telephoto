package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Responds to keyboard events to zoom and pan. */
internal data class KeyboardActionsElement(
  private var canPan: () -> Boolean,
  private var onZoom: (Float) -> Unit,
  private var onPan: (DpOffset) -> Unit,
) : ModifierNodeElement<KeyboardActionsNode>() {
  override fun create(): KeyboardActionsNode {
    return KeyboardActionsNode(
      canPan = canPan,
      onZoom = onZoom,
      onPan = onPan,
    )
  }

  override fun update(node: KeyboardActionsNode) {
    node.update(
      canPan = canPan,
      onZoom = onZoom,
      onPan = onPan,
    )
  }
}

internal class KeyboardActionsNode(
  private var canPan: () -> Boolean,
  private var onZoom: (Float) -> Unit,
  private var onPan: (DpOffset) -> Unit,
) : Modifier.Node(), KeyInputModifierNode {

  fun update(
    canPan: () -> Boolean,
    onZoom: (Float) -> Unit,
    onPan: (DpOffset) -> Unit,
  ) {
    this.canPan = canPan
    this.onZoom = onZoom
    this.onPan = onPan
  }

  override fun onKeyEvent(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) {
      return false
    }

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
    val shortcutDetector: KeyboardShortcutDetector = AndroidKeyboardShortcutDetector

    when (val it = shortcutDetector.detect(event)) {
      is KeyboardShortcut.Zoom -> {
        when (it.direction) {
          KeyboardShortcut.ZoomDirection.In -> onZoom(zoomStep)
          KeyboardShortcut.ZoomDirection.Out -> onZoom(1 / zoomStep)
        }
        return true
      }
      is KeyboardShortcut.Pan -> {
        if (canPan()) {
          val multiplier = when (it.type) {
            KeyboardShortcut.PanType.ShortPan -> 1f
            KeyboardShortcut.PanType.LongPan -> 17f // Copied from Chrome.
          }
          val multipliedStep = panStep * multiplier
          val offset = when (it.direction) {
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

  override fun onPreKeyEvent(event: KeyEvent): Boolean {
    return false
  }
}
