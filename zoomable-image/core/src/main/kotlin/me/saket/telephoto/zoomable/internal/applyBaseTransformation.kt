@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.zoomable.internal

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import me.saket.telephoto.zoomable.RealZoomableState
import me.saket.telephoto.zoomable.ZoomableState

/** Uses [ZoomableState]'s positioning, but ignores all user zoom & pan. */
@Stable
internal fun Modifier.applyBaseTransformation(state: ZoomableState): Modifier {
  return graphicsLayer {
    val transformation = state.contentTransformation
    scaleX = transformation.scaleMetadata.initialScale.scaleX
    scaleY = transformation.scaleMetadata.initialScale.scaleY
    transformOrigin = transformation.transformOrigin

    check(state is RealZoomableState)
    state.currentGestureStateInputs?.let { inputs ->
      val offset = -(inputs.baseOffset * transformation.scaleMetadata.initialScale)
      translationX = offset.x
      translationY = offset.y
    }
  }
}
