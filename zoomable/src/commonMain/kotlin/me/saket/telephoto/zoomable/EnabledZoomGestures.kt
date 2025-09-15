@file:Suppress("ConstPropertyName")

package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import kotlin.jvm.JvmInline

/**
 * Gestures that [Modifier.zoomable][zoomable] will observe and respond to.
 *
 * This acts as a simple toggle bag for different gesture types. To control _how_ [Modifier.zoomable][zoomable]
 * responds to them, use the params defined in [rememberZoomableState].
 *
 * Presets:
 *
 * - [EnabledZoomGestures.ZoomAndPan]
 * - [EnabledZoomGestures.ZoomOnly]
 * - [EnabledZoomGestures.PanOnly]
 * - [EnabledZoomGestures.None]
 *
 * Usage:
 *
 * ```
 * EnabledZoomGestures(
 *   zoom = true,
 *   pan = true,
 * )
 * ```
 */
@Immutable
@JvmInline
value class EnabledZoomGestures private constructor(
  private val flags: Int
) {
  companion object {
    val ZoomAndPan = EnabledZoomGestures(Flag.PinchToZoom or Flag.QuickZoom or Flag.Pan)
    val ZoomOnly = EnabledZoomGestures(Flag.PinchToZoom or Flag.QuickZoom)
    val PanOnly = EnabledZoomGestures(Flag.Pan)
    val None = EnabledZoomGestures(0)
  }

  val zoom: Boolean
    get() = pinchToZoom || quickZoom

  val pan: Boolean
    get() = flags and Flag.Pan != 0

  internal val pinchToZoom: Boolean
    get() = flags and Flag.PinchToZoom != 0

  internal val quickZoom: Boolean
    get() = flags and Flag.QuickZoom != 0

  /**
   * @param zoom whether to allow pinch-to-zoom and quick-zoom gestures.
   *
   * @param pan whether to allow panning with a single finger. When false, panning
   * with two fingers while zooming is still allowed.
   */
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
  ) = EnabledZoomGestures(
    zoom = zoom,
    pan = pan,
  )

  override fun toString(): String {
    return "EnabledZoomGestures(zoom=$zoom, pan=$pan)"
  }

  private object Flag {
    const val PinchToZoom = 1 shl 0
    const val QuickZoom = 1 shl 1
    const val Pan = 1 shl 2
  }
}

internal val EnabledZoomGestures.Companion.NoQuickZoom: EnabledZoomGestures
  get() = EnabledZoomGestures(pinchToZoom = true, quickZoom = false, pan = true)
