package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Responds to keyboard events to zoom and pan
 */
internal data class KeyboardActionsElement(
  private var zoomStep: Float,
  private var panStep: Dp,
  private var canPan: () -> Boolean,
  private var onZoom: (Float) -> Unit,
  private var onResetZoom: () -> Unit,
  private var onPan: (DpOffset) -> Unit,
) : ModifierNodeElement<KeyboardActionsNode>() {
  override fun create(): KeyboardActionsNode {
    return KeyboardActionsNode(
      zoomStep = zoomStep,
      panStep = panStep,
      canPan = canPan,
      onZoom = onZoom,
      onResetZoom = onResetZoom,
      onPan = onPan,
    )
  }

  override fun update(node: KeyboardActionsNode) {
    node.update(
      zoomStep = zoomStep,
      panStep = panStep,
      canPan = canPan,
      onZoom = onZoom,
      onResetZoom = onResetZoom,
      onPan = onPan,
    )
  }
}

internal class KeyboardActionsNode(
  private var zoomStep: Float,
  private var panStep: Dp,
  private var canPan: () -> Boolean,
  private var onZoom: (Float) -> Unit,
  private var onResetZoom: () -> Unit,
  private var onPan: (DpOffset) -> Unit,
) : Modifier.Node(), KeyInputModifierNode {
  override fun onKeyEvent(event: KeyEvent): Boolean {
    //TODO: how to detect the correct key combos?
    println("onKeyEvent: ${event.key} isCtrlPressed=${event.isCtrlPressed} isMetaPressed:${event.isMetaPressed}")
    when {
      event.key == Key.ZoomIn || event.key == Key.Plus || event.key == Key.Equals -> {
        if (event.type == KeyEventType.KeyDown) {
          onZoom(zoomStep)
        }
        return true
      }
      event.key == Key.ZoomOut || event.key == Key.Minus -> {
        if (event.type == KeyEventType.KeyDown) {
          onZoom(1 / zoomStep)
        }
        return true
      }
      event.key == Key.Zero -> {
        if (event.type == KeyEventType.KeyDown) {
          onResetZoom()
        }
        return true
      }
    }
    if (canPan()) {
      when (event.key) {
        Key.DirectionUp -> {
          val offset = DpOffset(x = 0.dp, y = -panStep)
          if (event.type == KeyEventType.KeyDown) {
            onPan(offset)
          }
          return true
        }
        Key.DirectionDown -> {
          val offset = DpOffset(x = 0.dp, y = panStep)
          if (event.type == KeyEventType.KeyDown) {
            onPan(offset)
          }
          return true
        }
        Key.DirectionLeft -> {
          val offset = DpOffset(x = -panStep, y = 0.dp)
          if (event.type == KeyEventType.KeyDown) {
            onPan(offset)
          }
          return true
        }
        Key.DirectionRight -> {
          val offset = DpOffset(x = panStep, y = 0.dp)
          if (event.type == KeyEventType.KeyDown) {
            onPan(offset)
          }
          return true
        }
      }
    }
    return false
  }

  override fun onPreKeyEvent(event: KeyEvent): Boolean {
    return false
  }

  fun update(
    zoomStep: Float,
    panStep: Dp,
    canPan: () -> Boolean,
    onZoom: (Float) -> Unit,
    onResetZoom: () -> Unit,
    onPan: (DpOffset) -> Unit,
  ) {
    this.zoomStep = zoomStep
    this.panStep = panStep
    this.canPan = canPan
    this.zoomStep = zoomStep
    this.onZoom = onZoom
    this.onResetZoom = onResetZoom
    this.onPan = onPan
  }
}
