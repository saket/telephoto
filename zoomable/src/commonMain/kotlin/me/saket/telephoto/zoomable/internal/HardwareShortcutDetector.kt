package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFold
import androidx.compose.ui.util.fastForEach
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.zoomable.internal.KeyboardShortcut.PanDirection
import me.saket.telephoto.zoomable.internal.KeyboardShortcut.ZoomDirection

internal interface HardwareShortcutDetector {
  fun detectKey(event: KeyEvent): KeyboardShortcut?
  fun detectScroll(event: PointerEvent): KeyboardShortcut?

  companion object {
    // todo: expect/actual this for all supported targets.
    //  - will it be easier to do all detection in one place and expect/actual the modifier keys?
    val Platform: HardwareShortcutDetector get() = AndroidHardwareShortcutDetector
  }
}

internal sealed interface KeyboardShortcut {
  @Poko class Zoom(
    val direction: ZoomDirection,
    val centroid: Offset = Offset.Unspecified,
    val zoomFactor: Float = DefaultZoomFactor,
  ) : KeyboardShortcut

  @Poko class Pan(
    val direction: PanDirection,
    val panOffset: Dp = DefaultPanOffset,
  ) : KeyboardShortcut

  enum class ZoomDirection {
    In,
    Out,
  }

  enum class PanDirection {
    Up,
    Down,
    Left,
    Right,
  }

  @Suppress("ConstPropertyName")
  companion object {
    const val DefaultZoomFactor = 1.2f
    val DefaultPanOffset = 50.dp
  }
}

internal object AndroidHardwareShortcutDetector : HardwareShortcutDetector {
  override fun detectKey(event: KeyEvent): KeyboardShortcut? {
    // Note for self: Some devices/peripherals have dedicated zoom buttons that map to Key.ZoomIn
    // and Key.ZoomOut. Examples: Samsung Galaxy Camera, a motorcycle handlebar controller.
    if (event.key == Key.ZoomIn || (event.utf16CodePoint == '+'.code)) {
      return KeyboardShortcut.Zoom(ZoomDirection.In)
    } else if (event.key == Key.ZoomOut || (event.utf16CodePoint == '-'.code)) {
      return KeyboardShortcut.Zoom(ZoomDirection.Out)
    }

    val panDirection = when (event.key) {
      Key.DirectionUp -> PanDirection.Up
      Key.DirectionDown -> PanDirection.Down
      Key.DirectionLeft -> PanDirection.Left
      Key.DirectionRight -> PanDirection.Right
      else -> null
    }
    if (panDirection != null) {
      return KeyboardShortcut.Pan(
        direction = panDirection,
        panOffset = KeyboardShortcut.DefaultPanOffset * if (event.isAltPressed) 17f else 1f,
      )
    }

    return null
  }

  override fun detectScroll(event: PointerEvent): KeyboardShortcut? {
    val scrollDeltaY = event.changes.fastFold(0f) { acc, c ->
      acc + (if (c.isConsumed) 0f else c.scrollDelta.y)
    }
    return when (scrollDeltaY) {
      0f -> null
      else -> {
        event.changes.fastForEach {
          it.consume()
        }
        KeyboardShortcut.Zoom(
          direction = if (scrollDeltaY < 0f) ZoomDirection.In else ZoomDirection.Out,
          centroid = event.changes[0].position,
        )
      }
    }
  }
}
