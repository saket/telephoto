package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import me.saket.telephoto.zoomable.GestureState
import me.saket.telephoto.zoomable.UserZoomFactor

@AndroidParcelize
@Suppress("DataClassPrivateConstructor")
internal data class ZoomableSavedState private constructor(
  private val offsetX: Float?,
  private val offsetY: Float?,
  private val userZoom: Float?
) : AndroidParcelable {

  constructor(gestureState: GestureState?) : this(
    offsetX = gestureState?.offset?.x,
    offsetY = gestureState?.offset?.y,
    userZoom = gestureState?.userZoomFactor?.value
  )

  fun asGestureState(): GestureState? {
    return GestureState(
      offset = Offset(
        x = offsetX ?: return null,
        y = offsetY ?: return null
      ),
      userZoomFactor = UserZoomFactor(
        value = userZoom ?: return null
      ),
      lastCentroid = Offset.Zero,
    )
  }
}
