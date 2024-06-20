package me.saket.telephoto.zoomable.internal

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFold
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.zoomable.internal.KeyboardShortcut.PanDirection
import me.saket.telephoto.zoomable.internal.KeyboardShortcut.ZoomDirection
import kotlin.math.absoluteValue

@Immutable
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
    if (event.key == Key.ZoomIn || (event.key == Key.Equals && event.isCtrlPressed)) {
      return KeyboardShortcut.Zoom(ZoomDirection.In)
    } else if (event.key == Key.ZoomOut || (event.key == Key.Minus && event.isCtrlPressed)) {
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
    if (!event.keyboardModifiers.isAltPressed) {
      // Google Photos does not require any modifier key to be pressed for zooming into
      // images using mouse scroll. Telephoto does not follow the same pattern because
      // it might migrate to 2D scrolling in the future for panning content once Compose
      // UI supports it.
      return null
    }
    return when (val scrollY = event.calculateScrollY()) {
      0f -> null
      else -> KeyboardShortcut.Zoom(
        direction = if (scrollY < 0f) ZoomDirection.In else ZoomDirection.Out,
        centroid = event.changes[0].position,
        // Scroll delta always seems to be either 1f or -1f depending on the direction.
        // Although some mice are capable of sending precise scrolls, I'm assuming
        // Android coerces them to be at least (+/-)1f.
        zoomFactor = KeyboardShortcut.DefaultZoomFactor * scrollY.absoluteValue,
      )
    }
  }

  private fun PointerEvent.calculateScrollY(): Float {
    return changes.fastFold(0f) { acc, c ->
      acc + c.scrollDelta.y
    }
  }
}
