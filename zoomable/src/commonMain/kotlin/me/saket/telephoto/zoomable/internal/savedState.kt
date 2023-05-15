package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import me.saket.telephoto.zoomable.ContentZoom
import me.saket.telephoto.zoomable.RawTransformation

internal expect class ZoomableSavedState(
  offsetX: Float?,
  offsetY: Float?,
  userZoom: Float?
) {
  val offsetX: Float?
  val offsetY: Float?
  val userZoom: Float?
}

internal fun ZoomableSavedState.gestureTransformation(): RawTransformation? {
  return RawTransformation(
    offset = Offset(
      x = offsetX ?: return null,
      y = offsetY ?: return null
    ),
    zoom = ContentZoom(
      baseZoom = ScaleFactor(0f, 0f), // Will get recalculated after restoration.
      userZoom = userZoom ?: return null
    ),
    lastCentroid = Offset.Zero,
    contentSize = Size.Zero,  // Will get recalculated after restoration.
  )
}

internal fun ZoomableSavedState(transformation: RawTransformation?) =
  ZoomableSavedState(
    offsetX = transformation?.offset?.x,
    offsetY = transformation?.offset?.y,
    userZoom = transformation?.zoom?.userZoom
  )
