package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/** Like [Rect.overlaps], but does not require allocating a Rect. */
internal fun Rect.overlaps(otherOffset: Offset, otherSize: Size): Boolean {
  val otherLeft = otherOffset.x
  val otherRight = otherLeft + otherSize.width
  val otherTop = otherOffset.x
  val otherBottom = otherTop + otherSize.height

  if (right <= otherLeft || otherRight <= left)
    return false
  if (bottom <= otherTop || otherBottom <= top)
    return false
  return true
}
