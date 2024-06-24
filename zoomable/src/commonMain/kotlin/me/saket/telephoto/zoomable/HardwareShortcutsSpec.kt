package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.zoomable.internal.HardwareShortcutDetector

@Poko
@Immutable
class HardwareShortcutsSpec(
  val enabled: Boolean = true,
  val shortcutDetector: HardwareShortcutDetector = HardwareShortcutDetector.Default,
) {

  companion object {
    val Disabled = HardwareShortcutsSpec(enabled = false)
  }
}
