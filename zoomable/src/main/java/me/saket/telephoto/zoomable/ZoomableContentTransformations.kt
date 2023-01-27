package me.saket.telephoto.zoomable

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer

data class ZoomableContentTransformations(
  val scale: Float = 1f,
  val rotationZ: Float = 0f,
  val offset: Offset = Offset.Zero
)

fun Modifier.graphicsLayer(transformations: ZoomableContentTransformations): Modifier {
  // todo: optimize these. use graphicsLayer only when necessary.
  return graphicsLayer {
    scaleX = transformations.scale
    scaleY = transformations.scale
    rotationZ = transformations.rotationZ
    translationX = transformations.offset.x
    translationY = transformations.offset.y
  }
}
