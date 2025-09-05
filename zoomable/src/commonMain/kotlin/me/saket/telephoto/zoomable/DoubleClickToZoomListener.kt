package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.internal.maxScale
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.spatial.CoordinateSystem
import me.saket.telephoto.zoomable.spatial.SpatialOffset

/**
 * Implement this interface for reacting to double clicks on `Modifier.zoomable`'s content.
 * By default, [DoubleClickToZoomListener.cycle] is used.
 */
@Immutable
fun interface DoubleClickToZoomListener {
  companion object {
    /**
     * Cycles between [maxZoomFactor] and the minimum zoom factor on double clicks.
     * When [maxZoomFactor] is null, [ZoomSpec.maximum] is used.
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

  @ExperimentalTelephotoApi
  suspend fun CoordinateSystem.onDoubleClick(
    state: ZoomableState,
    centroid: SpatialOffset,
  ) {
    onDoubleClick(
      state = state,
      centroid = with(state.coordinateSystem) {
        centroid.offsetIn(CoordinateSpace.Viewport)
      }
    )
  }

  /**
   * Toggles between [ZoomSpec.maximum] and the [ZoomSpec.minimum] on double clicks.
   */
  @Deprecated(
    message = "Use DoubleClickToZoomListener.cycle() instead",
    replaceWith = ReplaceWith("DoubleClickToZoomListener.cycle()"),
  )
  data object ToggleBetweenMinAndMax : DoubleClickToZoomListener {
    override suspend fun onDoubleClick(state: ZoomableState, centroid: Offset) {
      cycle().onDoubleClick(state, centroid)
    }
  }
}

/**
 * See [DoubleClickToZoomListener.cycle].
 */
@OptIn(ExperimentalTelephotoApi::class)
private data class CycleZoomOnDoubleClick(private val maxZoomFactor: Float? = null) : DoubleClickToZoomListener {
  override suspend fun CoordinateSystem.onDoubleClick(state: ZoomableState, centroid: SpatialOffset) {
    val transformation = state.contentTransformation.takeIf { it.isSpecified } ?: return // Content isn't ready yet
    val maxZoomFactor = maxZoomFactor ?: state.zoomSpec.maximum.factor
    val isAtMaxZoom = maxZoomFactor - transformation.scale.maxScale < 0.05f

    if (isAtMaxZoom) {
      state.resetZoom()
    } else {
      state.zoomTo(
        zoomFactor = maxZoomFactor,
        centroid = centroid,
      )
    }
  }

  override suspend fun onDoubleClick(state: ZoomableState, centroid: Offset) {
    with(state.coordinateSystem) {
      onDoubleClick(state, SpatialOffset(centroid, CoordinateSpace.Viewport))
    }
  }
}
