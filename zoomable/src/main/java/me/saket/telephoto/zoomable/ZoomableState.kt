package me.saket.telephoto.zoomable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun rememberZoomableState(): ZoomableState {
  return remember { ZoomableState() }
}

@Stable
class ZoomableState {
  var transformations by mutableStateOf(ZoomableContentTransformations())
}
