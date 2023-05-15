package me.saket.telephoto.zoomable.internal

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
internal actual class ZoomableSavedState actual constructor(
  actual val offsetX: Float?,
  actual val offsetY: Float?,
  actual val userZoom: Float?,
) : Parcelable
