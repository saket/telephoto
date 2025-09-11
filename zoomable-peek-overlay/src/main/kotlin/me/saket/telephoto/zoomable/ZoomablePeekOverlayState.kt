package me.saket.telephoto.zoomable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalView

/**
 * Create a [ZoomablePeekOverlayState] that can be used with
 * [Modifier.zoomablePeekOverlay][zoomablePeekOverlay].
 */
@Composable
fun rememberZoomablePeekOverlayState(): ZoomablePeekOverlayState {
  val zoomableState = rememberZoomableState(
    zoomSpec = DynamicZoomSpec.fixed(
      ZoomSpec(
        maximum = ZoomLimit(factor = 1f, overzoomEffect = OverzoomEffect.NoLimits),
        minimum = ZoomLimit(factor = 1f, overzoomEffect = OverzoomEffect.RubberBanding),
      )
    ),
    hardwareShortcutsSpec = HardwareShortcutsSpec.Disabled,
  )
  val graphicsLayer = if (LocalView.current.isHardwareAccelerated) {
    rememberGraphicsLayer()
  } else {
    null  // GraphicsLayer does not support SW acceleration.
  }
  return remember(zoomableState, graphicsLayer) {
    RealZoomablePeekOverlayState(
      zoomableState = zoomableState,
      graphicsLayer = graphicsLayer,
    )
  }.also {
    it.DisplayOverlayEffect()
  }
}

/** State class for [Modifier.zoomablePeekOverlay][zoomablePeekOverlay]. */
@Stable
sealed interface ZoomablePeekOverlayState {
  val zoomableState: ZoomableState

  val isZoomedIn: Boolean
}
