package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import dev.drewhamilton.poko.Poko

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
