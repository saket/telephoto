package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.zoomable.internal.KeyboardShortcutDetector

@Poko
@Immutable
internal class HotkeysSpec(
  val enabled: Boolean = true,
  val detector: KeyboardShortcutDetector = KeyboardShortcutDetector.Platform,
) {

  companion object {
    val Disabled = HotkeysSpec(enabled = false)
  }
}
