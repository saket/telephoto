package me.saket.telephoto.zoomable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

@Composable
fun rememberZoomableState(
  rotationEnabled: Boolean = false,
  maxZoomFactor: Float = 1f,
): ZoomableState {
  return remember { ZoomableState() }.apply {
    this.rotationEnabled = rotationEnabled
    this.maxZoomFactor = maxZoomFactor
  }
}

@Stable
class ZoomableState internal constructor() {
  /** todo: doc */
  var transformations by mutableStateOf(ZoomableContentTransformations.Empty)

  internal var rotationEnabled: Boolean = false
  internal var maxZoomFactor: Float = 1f

  internal var unscaledContentSize: IntSize by mutableStateOf(IntSize.Zero)
  internal var contentLayoutSize by mutableStateOf(IntSize.Zero)

  /** todo: doc */
  fun setUnscaledContentSize(size: IntSize?) {
    unscaledContentSize = size ?: IntSize.Zero
  }

  /** todo: doc */
  fun setUnscaledContentSize(size: Size?) {
    setUnscaledContentSize(size?.roundToIntSize())
  }
}

private fun Size.roundToIntSize(): IntSize {
  return IntSize(width = width.roundToInt(), height = height.roundToInt())
}
