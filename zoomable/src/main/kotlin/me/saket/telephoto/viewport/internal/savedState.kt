package me.saket.telephoto.viewport.internal

import android.os.Parcelable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ScaleFactor
import kotlinx.parcelize.Parcelize
import me.saket.telephoto.viewport.ContentZoom
import me.saket.telephoto.viewport.GestureTransformation

@Parcelize
internal data class ZoomableViewportSavedState(
  private val offsetX: Float?,
  private val offsetY: Float?,
  val viewportZoom: Float?,
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
        viewportZoom = viewportZoom ?: return null
      ),
      lastCentroid = Offset.Zero
    )
  }
}

internal fun ZoomableViewportSavedState(transformation: GestureTransformation?) =
  ZoomableViewportSavedState(
    offsetX = transformation?.offset?.x,
    offsetY = transformation?.offset?.y,
    viewportZoom = transformation?.zoom?.viewportZoom
  )
