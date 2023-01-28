package me.saket.telephoto.zoomable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
}
