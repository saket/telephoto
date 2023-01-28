package me.saket.telephoto.zoomable

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

data class ZoomableContentTransformations(
  val scale: Float,
  val rotationZ: Float,
  val offset: Offset,
  val transformOrigin: TransformOrigin,
) {
  internal companion object {
    val Empty = ZoomableContentTransformations(
      scale = 1f,
      rotationZ = 0f,
      offset = Offset.Zero,
      transformOrigin = TransformOrigin.Center
    )
  }
}

fun Modifier.graphicsLayer(transformations: ZoomableContentTransformations): Modifier {
  // todo: optimize these. use graphicsLayer only when necessary.
  return graphicsLayer {
    scaleX = transformations.scale
    scaleY = transformations.scale
    rotationZ = transformations.rotationZ
    translationX = transformations.offset.x
    translationY = transformations.offset.y
    transformOrigin = transformations.transformOrigin
  }
}
