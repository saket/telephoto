package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import me.saket.telephoto.zoomable.GestureState
import me.saket.telephoto.zoomable.UserZoomFactor

@AndroidParcelize
internal data class ZoomableSavedState(
  private val offsetX: Float?,
  private val offsetY: Float?,
  private val userZoom: Float?
) : AndroidParcelable {

  constructor(transformation: GestureState?) : this(
    offsetX = transformation?.offset?.x,
    offsetY = transformation?.offset?.y,
    userZoom = transformation?.userZoomFactor?.value
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
      // Content size will get recalculated after restoration. It's not great that the size
      // is stored as part of the "gesture state", but I should be able to remove this once
      // Modifier.zoomable() is able to calculate its content size synchronously.
      contentSize = Size.Zero,
      isPlaceholder = false,  // todo: is this correct?
    )
  }
}
