package me.saket.telephoto.zoomable

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * todo: doc.
 *
 * All transformations are done from `0,0`.
 * */
data class ZoomableContentTransformations(
  val viewportSize: Size,
  val scale: Float,
  val rotationZ: Float,
  val offset: Offset,
  val transformOrigin: TransformOrigin = when {
    // Center content when it's zoomed out and appears smaller than its viewport.
    scale < 1f -> TransformOrigin.Center
    else -> TransformOrigin(0f, 0f)
  },
) {
  companion object {
    val Empty = ZoomableContentTransformations(
      viewportSize = Size.Unspecified,
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
