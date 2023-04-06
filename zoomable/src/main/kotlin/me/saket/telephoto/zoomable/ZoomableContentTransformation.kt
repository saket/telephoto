package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ScaleFactor

/**
 * todo: doc.
 */
@Immutable
data class ZoomableContentTransformation(
  val scale: ScaleFactor,
  val rotationZ: Float,
  val offset: Offset,
  val transformOrigin: TransformOrigin = TransformOriginAtZero,
) {
  companion object {
    private val TransformOriginAtZero = TransformOrigin(0f, 0f)
  }
}
