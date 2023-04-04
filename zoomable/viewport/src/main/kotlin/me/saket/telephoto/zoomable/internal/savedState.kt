package me.saket.telephoto.zoomable.internal

import android.os.Parcelable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ScaleFactor
import kotlinx.parcelize.Parcelize
import me.saket.telephoto.zoomable.ContentZoom
import me.saket.telephoto.zoomable.GestureTransformation

@Parcelize
internal data class ZoomableSavedState(
  private val offsetX: Float?,
  private val offsetY: Float?,
  private val userZoom: Float?,
) : Parcelable {

  fun gestureTransformation(): GestureTransformation? {
    return GestureTransformation(
      offset = Offset(
        x = offsetX ?: return null,
        y = offsetY ?: return null
      ),
      zoom = ContentZoom(
        // Base multiplier will be replaced by the actual value when this restored state is consumed.
        baseZoom = ScaleFactor(0f, 0f),
        userZoom = userZoom ?: return null
      ),
      lastCentroid = Offset.Zero
    )
  }
}

internal fun ZoomableSavedState(transformation: GestureTransformation?) =
  ZoomableSavedState(
    offsetX = transformation?.offset?.x,
    offsetY = transformation?.offset?.y,
    userZoom = transformation?.zoom?.userZoom
  )
