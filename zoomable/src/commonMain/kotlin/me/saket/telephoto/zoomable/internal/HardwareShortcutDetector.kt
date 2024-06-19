package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEvent
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.zoomable.internal.KeyboardShortcut.PanDirection
import me.saket.telephoto.zoomable.internal.KeyboardShortcut.PanType
import me.saket.telephoto.zoomable.internal.KeyboardShortcut.ZoomDirection

internal interface HardwareShortcutDetector {
  fun detect(event: KeyEvent): KeyboardShortcut?
  fun detect(event: PointerEvent): KeyboardShortcut?

  companion object {
    // todo: expect/actual this for all supported targets.
    val Platform: HardwareShortcutDetector get() = AndroidHardwareShortcutDetector
  }
}

internal sealed interface KeyboardShortcut {
  @Poko class Zoom(
    val direction: ZoomDirection,
    val centroid: Offset = Offset.Unspecified,
  ) : KeyboardShortcut

  @Poko class Pan(
    val direction: PanDirection,
    val type: PanType,
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

  enum class PanType {
    ShortPan,
    LongPan,
  }
}

internal object AndroidHardwareShortcutDetector : HardwareShortcutDetector {
  override fun detect(event: KeyEvent): KeyboardShortcut? {
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
        type = when {
          event.isAltPressed -> PanType.LongPan
          else -> PanType.ShortPan
        },
      )
    }

    return null
  }

  override fun detect(event: PointerEvent): KeyboardShortcut {
    val isZoomingIn = event.changes[0].scrollDelta.y < 0f
    return KeyboardShortcut.Zoom(
      direction = if (isZoomingIn) ZoomDirection.In else ZoomDirection.Out,
      centroid = event.changes[0].position,
    )
  }
}
