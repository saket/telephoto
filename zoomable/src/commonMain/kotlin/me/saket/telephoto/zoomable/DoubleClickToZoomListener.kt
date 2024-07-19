package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.zoomable.internal.maxScale

/**
 * Implement this interface for reacting to double clicks on `Modifier.zoomable`'s content.
 * By default, [DoubleClickToZoomListener.cycle] is used.
 */
@Immutable
fun interface DoubleClickToZoomListener {
  companion object {
    /**
     * Cycles between [maxZoomFactor] and the minimum zoom factor on double clicks.
     * When [maxZoomFactor] is null, [ZoomSpec.maxZoomFactor] is used.
     */
    @Stable
    fun cycle(
      maxZoomFactor: Float? = null
    ): DoubleClickToZoomListener = CycleZoomOnDoubleClick(maxZoomFactor)
  }

  suspend fun onDoubleClick(
    state: ZoomableState,
    centroid: Offset,
  )

  /**
   * Toggles between [ZoomSpec.maxZoomFactor] and the minimum zoom factor on double clicks.
   */
  @Deprecated(
    message = "Use DoubleClickToZoomListener.Cycle() instead",
    replaceWith = ReplaceWith("DoubleClickToZoomListener.cycle()"),
  )
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

/**
 * See [DoubleClickToZoomListener.cycle].
 */
@Poko
@Immutable
private class CycleZoomOnDoubleClick(private val maxZoomFactor: Float? = null) : DoubleClickToZoomListener {
  override suspend fun onDoubleClick(state: ZoomableState, centroid: Offset) {
    val transformation = state.contentTransformation.takeIf { it.isSpecified } ?: return // Content isn't ready yet
    val maxZoomFactor = this.maxZoomFactor ?: state.zoomSpec.maxZoomFactor
    val isAtMaxZoom = maxZoomFactor - transformation.scale.scaleX < 0.05f

    if (isAtMaxZoom) {
      state.resetZoom()
    } else {
      state.zoomTo(
        zoomFactor = maxZoomFactor,
        centroid = centroid,
      )
    }
  }
}
