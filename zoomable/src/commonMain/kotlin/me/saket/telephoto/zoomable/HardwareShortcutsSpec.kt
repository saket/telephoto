package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.ui.focus.FocusRequester
import dev.drewhamilton.poko.Poko

/**
 * Describes how keyboard and mouse shortcuts are handled. When [enabled], [Modifier.zoomable][zoomable]'s
 * content will participate in the focus system to receive hardware events.
 *
 * Keep in mind that hardware shortcuts will only work when your zoomable content is focused.
 * To do this automatically, use a [FocusRequester]:
 *
 * ```
 * val focusRequester = remember { FocusRequester() }
 * LaunchedEffect(Unit) {
 *   // Automatically request focus when the image is displayed. This assumes there
 *   // is only one zoomable image present in the hierarchy. If you're displaying
 *   // multiple images in a pager, apply this only for the active page.
 *   focusRequester.requestFocus()
 * }
 *
 * ZoomableImage(
 *   modifier = Modifier.focusRequester(focusRequester),
 * )
 * ```
 */
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
