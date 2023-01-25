package me.saket.telephoto.zoomable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun rememberZoomableState(): ZoomableState {
  return remember { ZoomableState() }
}

@Stable
class ZoomableState {
  var transformations by mutableStateOf(ZoomableTransformations())
}

data class ZoomableTransformations(
  val scale: Float = 1f,
  val rotationZ: Float = 0f,
  val offset: Offset = Offset.Zero
)

fun Modifier.graphicsLayer(transformations: ZoomableTransformations): Modifier {
  // todo: optimize these. use graphicsLayer only when necessary.
  return graphicsLayer {
    scaleX = transformations.scale
    scaleY = transformations.scale
    rotationZ = transformations.rotationZ
    translationX = transformations.offset.x
    translationY = transformations.offset.y
  }
}
