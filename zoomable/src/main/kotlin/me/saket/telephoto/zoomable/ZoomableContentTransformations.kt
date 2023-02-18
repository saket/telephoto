package me.saket.telephoto.zoomable

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ScaleFactor

/**
 * todo: doc.
 *
 * All transformations are done from `0,0`.
 * */
data class ZoomableContentTransformations(
  val viewportSize: Size,
  val scale: ScaleFactor,
  val rotationZ: Float,
  val offset: Offset,
  val transformOrigin: TransformOrigin = TransformOriginAtZero,
) {
  companion object {
    private val TransformOriginAtZero = TransformOrigin(0f, 0f)
  }
}

fun Modifier.graphicsLayer(transformations: ZoomableContentTransformations): Modifier {
  // todo: optimize these. use graphicsLayer only when necessary.
  return graphicsLayer {
    scaleX = transformations.scale.scaleX
    scaleY = transformations.scale.scaleY
    rotationZ = transformations.rotationZ
    translationX = transformations.offset.x
    translationY = transformations.offset.y
    transformOrigin = transformations.transformOrigin
  }
}
