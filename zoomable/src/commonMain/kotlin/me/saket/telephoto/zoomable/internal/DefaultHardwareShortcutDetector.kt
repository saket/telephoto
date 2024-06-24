package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.util.fastFold
import androidx.compose.ui.util.fastForEach
import me.saket.telephoto.zoomable.HardwareShortcutDetector
import kotlin.math.absoluteValue

internal object DefaultHardwareShortcutDetector : HardwareShortcutDetector {
  override fun detectKey(event: KeyEvent): HardwareShortcutDetector.ShortcutEvent? {
    // Note for self: Some devices/peripherals have dedicated zoom buttons that map to Key.ZoomIn
    // and Key.ZoomOut. Examples include: Samsung Galaxy Camera, a motorcycle handlebar controller.
    if (event.key == Key.ZoomIn || event.isZoomInEvent()) {
      return HardwareShortcutDetector.ShortcutEvent.Zoom(HardwareShortcutDetector.ShortcutEvent.ZoomDirection.In)
    } else if (event.key == Key.ZoomOut || (event.isZoomOutEvent())) {
      return HardwareShortcutDetector.ShortcutEvent.Zoom(HardwareShortcutDetector.ShortcutEvent.ZoomDirection.Out)
    }

    val panDirection = when (event.key) {
      Key.DirectionUp -> HardwareShortcutDetector.ShortcutEvent.PanDirection.Up
      Key.DirectionDown -> HardwareShortcutDetector.ShortcutEvent.PanDirection.Down
      Key.DirectionLeft -> HardwareShortcutDetector.ShortcutEvent.PanDirection.Left
      Key.DirectionRight -> HardwareShortcutDetector.ShortcutEvent.PanDirection.Right
      else -> null
    }
    return when (panDirection) {
      null -> null
      else -> HardwareShortcutDetector.ShortcutEvent.Pan(
        direction = panDirection,
        panOffset = HardwareShortcutDetector.ShortcutEvent.DefaultPanOffset * if (event.isAltPressed) 10f else 1f,
      )
    }
  }

  private fun KeyEvent.isZoomInEvent(): Boolean {
    return this.key == Key.Equals && when (HostPlatform.current) {
      HostPlatform.Android -> isCtrlPressed
      HostPlatform.Desktop -> isMetaPressed
    }
  }

  private fun KeyEvent.isZoomOutEvent(): Boolean {
    return key == Key.Minus && when (HostPlatform.current) {
      HostPlatform.Android -> isCtrlPressed
      HostPlatform.Desktop -> isMetaPressed
    }
  }

  override fun detectScroll(event: PointerEvent): HardwareShortcutDetector.ShortcutEvent? {
    if (!event.keyboardModifiers.isAltPressed) {
      // Google Photos does not require any modifier key to be pressed for zooming into
      // images using mouse scroll. Telephoto does not follow the same pattern because
      // it might migrate to 2D scrolling in the future for panning content once Compose
      // UI supports it.
      return null
    }
    return when (val scrollY = event.calculateScroll().y) {
      0f -> null
      else -> HardwareShortcutDetector.ShortcutEvent.Zoom(
        direction = if (scrollY < 0f) HardwareShortcutDetector.ShortcutEvent.ZoomDirection.In else HardwareShortcutDetector.ShortcutEvent.ZoomDirection.Out,
        centroid = event.calculateScrollCentroid(),
        // Scroll delta always seems to be either 1f or -1f depending on the direction.
        // Although some mice are capable of sending precise scrolls, I'm assuming
        // Android coerces them to be at least (+/-)1f.
        zoomFactor = HardwareShortcutDetector.ShortcutEvent.DefaultZoomFactor * scrollY.absoluteValue,
      )
    }
  }

  private fun PointerEvent.calculateScroll(): Offset {
    return changes.fastFold(Offset.Zero) { acc, c ->
      acc + c.scrollDelta
    }
  }

  private fun PointerEvent.calculateScrollCentroid(): Offset {
    check(type == PointerEventType.Scroll)
    var centroid = Offset.Zero
    var centroidWeight = 0f
    changes.fastForEach { change ->
      val position = change.position
      centroid += position
      centroidWeight++
    }
    return when (centroidWeight) {
      0f -> Offset.Unspecified
      else -> centroid / centroidWeight
    }
  }
}
