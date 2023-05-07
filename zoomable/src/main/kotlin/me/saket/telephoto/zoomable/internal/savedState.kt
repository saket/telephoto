package me.saket.telephoto.zoomable.internal

import android.os.Parcelable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import kotlinx.parcelize.Parcelize
import me.saket.telephoto.zoomable.ContentZoom
import me.saket.telephoto.zoomable.RawTransformation

@Parcelize
internal data class ZoomableSavedState(
  private val offsetX: Float?,
  private val offsetY: Float?,
  private val userZoom: Float?,
) : Parcelable {

  fun gestureTransformation(): RawTransformation? {
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
}

internal fun ZoomableSavedState(transformation: RawTransformation?) =
  ZoomableSavedState(
    offsetX = transformation?.offset?.x,
    offsetY = transformation?.offset?.y,
    userZoom = transformation?.zoom?.userZoom
  )
