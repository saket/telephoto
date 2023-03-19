package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ScaleFactor

/**
 * todo: doc.
 *
 * All transformations are done from `0,0`.
 *
 * @param viewportBounds Bounds of the parent composable that created this object. Relative to the
 * content's layout bounds.
 * */
@Immutable
data class ZoomableContentTransformation(
  val viewportBounds: Rect,
  val scale: ScaleFactor,
  val rotationZ: Float,
  val offset: Offset,
  val transformOrigin: TransformOrigin = TransformOriginAtZero,
) {
  companion object {
    private val TransformOriginAtZero = TransformOrigin(0f, 0f)
  }
}

fun Modifier.applyTransformation(transformation: ZoomableContentTransformation): Modifier {
  // todo: optimize these. use graphicsLayer only when necessary.
  return graphicsLayer {
    scaleX = transformation.scale.scaleX
    scaleY = transformation.scale.scaleY
    rotationZ = transformation.rotationZ
    translationX = transformation.offset.x
    translationY = transformation.offset.y
    transformOrigin = transformation.transformOrigin
  }
}
