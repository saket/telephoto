package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.zoomable.internal.DefaultHardwareShortcutDetector

@Immutable
interface HardwareShortcutDetector {
  companion object {
    val Default: HardwareShortcutDetector get() = DefaultHardwareShortcutDetector
  }

  /** Detect a keyboard shortcut or return `null` to ignore. */
  fun detectKey(event: KeyEvent): ShortcutEvent?

  /** Detect a mouse scroll shortcut or return `null` to ignore. */
  fun detectScroll(event: PointerEvent): ShortcutEvent?

  sealed interface ShortcutEvent {
    @Poko class Zoom(
      val direction: ZoomDirection,
      val zoomFactor: Float = DefaultZoomFactor,
      val centroid: Offset = Offset.Unspecified,
    ) : ShortcutEvent

    @Poko class Pan(
      val direction: PanDirection,
      val panOffset: Dp = DefaultPanOffset,
    ) : ShortcutEvent

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
}
