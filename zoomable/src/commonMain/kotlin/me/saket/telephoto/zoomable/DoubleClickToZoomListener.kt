package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import me.saket.telephoto.zoomable.DoubleClickToZoomListener.ToggleBetweenMinAndMax
import me.saket.telephoto.zoomable.internal.maxScale

/**
 * Implement this interface for reacting to double clicks on `Modifier.zoomable`'s content.
 * By default, [ToggleBetweenMinAndMax] is used.
 */
@Immutable
fun interface DoubleClickToZoomListener {
  suspend fun onDoubleClick(
    state: ZoomableState,
    centroid: Offset,
  )

  /**
   * Toggles between [ZoomSpec.maxZoomFactor] and the minimum zoom factor on double clicks.
   */
  data object ToggleBetweenMinAndMax : DoubleClickToZoomListener {
    override suspend fun onDoubleClick(state: ZoomableState, centroid: Offset) {
      val zoomFraction = state.zoomFraction ?: return // Content isn't ready yet.
      state.zoomTo(
        zoomFactor = if (zoomFraction < 0.95f) {
          state.zoomSpec.maxZoomFactor
        } else {
          state.contentTransformation.scaleMetadata.initialScale.maxScale
        },
        centroid = centroid,
      )
    }
  }
}
