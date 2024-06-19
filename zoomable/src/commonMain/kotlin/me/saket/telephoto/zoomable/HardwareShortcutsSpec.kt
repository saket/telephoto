package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.zoomable.internal.KeyboardShortcutDetector

@Poko
@Immutable
internal class HardwareShortcutsSpec(
  val enabled: Boolean = true,
  val detector: KeyboardShortcutDetector = KeyboardShortcutDetector.Platform,
) {

  companion object {
    val Disabled = HardwareShortcutsSpec(enabled = false)
  }
}
