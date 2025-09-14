@file:Suppress("ConstPropertyName")

package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import kotlin.jvm.JvmInline

/**
 * Gesture interactions that can be enabled for [Modifier.zoomable][zoomable].
 *
 * Presets:
 *
 * - [ZoomInteractions.ZoomAndPan]
 * - [ZoomInteractions.ZoomOnly]
 * - [ZoomInteractions.PanOnly]
 * - [ZoomInteractions.None]
 * */
@Immutable
@JvmInline
value class ZoomInteractions private constructor(
  private val flags: Int
) {
  companion object {
    val ZoomAndPan = ZoomInteractions(Flag.PinchToZoom or Flag.QuickZoom or Flag.Pan)
    val ZoomOnly = ZoomInteractions(Flag.PinchToZoom or Flag.QuickZoom)
    val PanOnly = ZoomInteractions(Flag.Pan)
    val None = ZoomInteractions(0)
  }

  val zoom: Boolean
    get() = pinchToZoom || quickZoom

  val pan: Boolean
    get() = flags and Flag.Pan != 0

  internal val pinchToZoom: Boolean
    get() = flags and Flag.PinchToZoom != 0

  internal val quickZoom: Boolean
    get() = flags and Flag.QuickZoom != 0

  constructor(
    zoom: Boolean = true,
    pan: Boolean = true,
  ) : this(
    pinchToZoom = zoom,
    quickZoom = zoom,
    pan = pan,
  )

  internal constructor(
    pinchToZoom: Boolean = true,
    quickZoom: Boolean = true,
    pan: Boolean = true,
  ) : this(
    (if (pinchToZoom) Flag.PinchToZoom else 0) or
      (if (quickZoom) Flag.QuickZoom else 0) or
      (if (pan) Flag.Pan else 0)
  )

  fun copy(
    zoom: Boolean = this.zoom,
    pan: Boolean = this.pan,
  ) = ZoomInteractions(
    zoom = zoom,
    pan = pan,
  )

  override fun toString(): String {
    return "ZoomInteractions(zoom=$zoom, pan=$pan)"
  }

  private object Flag {
    const val PinchToZoom = 1 shl 0
    const val QuickZoom = 1 shl 1
    const val Pan = 1 shl 2
  }
}
