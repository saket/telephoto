package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.zoomable.internal.KeyboardShortcut.PanDirection
import me.saket.telephoto.zoomable.internal.KeyboardShortcut.PanType
import me.saket.telephoto.zoomable.internal.KeyboardShortcut.ZoomDirection
import me.saket.telephoto.zoomable.internal.KeyboardShortcut.ZoomType

internal interface KeyboardShortcutDetector {
  fun detect(event: KeyEvent): KeyboardShortcut?

  companion object {
    // todo: expect/actual this for all supported targets.
    val Platform: KeyboardShortcutDetector get() = AndroidKeyboardShortcutDetector
  }
}

internal sealed interface KeyboardShortcut {
  @Poko class Zoom(
    val direction: ZoomDirection,
    val type: ZoomType,
  ) : KeyboardShortcut

  @Poko class Pan(
    val direction: PanDirection,
    val type: PanType,
  ) : KeyboardShortcut

  enum class ZoomDirection {
    In,
    Out,
  }

  enum class ZoomType {
    ShortZoom,
  }

  enum class PanDirection {
    Up,
    Down,
    Left,
    Right,
  }

  enum class PanType {
    ShortPan,
    LongPan,
  }
}

internal object AndroidKeyboardShortcutDetector : KeyboardShortcutDetector {
  override fun detect(event: KeyEvent): KeyboardShortcut? {
    // Note for self: Some devices/peripherals have dedicated zoom buttons that map to Key.ZoomIn
    // and Key.ZoomOut. Examples: Samsung Galaxy Camera, a motorcycle handlebar controller.
    if (event.key == Key.ZoomIn || (event.utf16CodePoint == '+'.code)) {
      return KeyboardShortcut.Zoom(ZoomDirection.In, ZoomType.ShortZoom)
    } else if (event.key == Key.ZoomOut || (event.utf16CodePoint == '-'.code)) {
      return KeyboardShortcut.Zoom(ZoomDirection.Out, ZoomType.ShortZoom)
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
        type = when {
          event.isAltPressed -> PanType.LongPan
          else -> PanType.ShortPan
        },
      )
    }

    return null
  }
}