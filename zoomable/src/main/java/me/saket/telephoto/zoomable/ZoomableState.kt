package me.saket.telephoto.zoomable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize

@Composable
fun rememberZoomableState(
  rotationEnabled: Boolean
): ZoomableState {
  return remember { ZoomableState() }.apply {
    this.rotationEnabled = rotationEnabled
  }
}

@Stable
class ZoomableState internal constructor() {
  /** todo: doc */
  var transformations by mutableStateOf(ZoomableContentTransformations())

  internal var rotationEnabled: Boolean = false
  internal var contentSize: Size by mutableStateOf(Size.Unspecified)

  fun setContentSize(size: IntSize) {
    contentSize = size.toSize()
  }
}
