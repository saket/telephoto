package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import me.saket.telephoto.zoomable.DoubleClickListener.ToggleBetweenMinAndMax
import me.saket.telephoto.zoomable.internal.maxScale

/**
 * Implement this interface for reacting to double clicks on `Modifier.zoomable`'s content.
 * By default, [ToggleBetweenMinAndMax] is used.
 */
@Immutable
fun interface DoubleClickListener {
  suspend fun onDoubleClick(
    state: ZoomableState,
    centroid: Offset,
  )

  /**
   * Toggles between [ZoomSpec.maxZoomFactor] and the minimum zoom factor on double clicks.
   */
  data object ToggleBetweenMinAndMax : DoubleClickListener {
    override suspend fun onDoubleClick(state: ZoomableState, centroid: Offset) {
      val zoomFraction = state.zoomFraction ?: return // Content isn't ready yet.
      state.zoomTo(
        zoomFactor = when {
          zoomFraction <= 0.05f -> state.zoomSpec.maxZoomFactor
          else -> state.contentTransformation.scaleMetadata.initialScale.maxScale
        },
        centroid = centroid,
        withAnimation = true,
      )
    }
  }
}
