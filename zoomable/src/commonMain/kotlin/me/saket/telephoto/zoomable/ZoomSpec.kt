package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import dev.drewhamilton.poko.Poko

@Poko
@Immutable
class ZoomSpec(
  /**
   * The maximum zoom level as a percentage of the content's unscaled/original size.
   * For example, a factor of 2.0 allows zooming up to 200% of the original size.
   *
   * If the content is initially displayed larger than its original size to fill the
   * viewport (based on its content scale), the maximum zoom limit is relative
   * to that displayed size instead of the original size.
   */
  val maximum: ZoomLimit = ZoomLimit(factor = 2f, overzoomEffect = OverzoomEffect.RubberBanding),

  /**
   * The minimum zoom level _relative_ to the content's base scale.
   * The base scale is calculated using the content's original size and the
   * [content scale][ZoomableState.contentScale]. For example, a factor of 1.0 ensures
   * the content cannot be zoomed out beyond this base scale.
   */
  val minimum: ZoomLimit = ZoomLimit(factor = 1f, overzoomEffect = OverzoomEffect.RubberBanding),
) {
  constructor(
    maxZoomFactor: Float = 2f,
    minZoomFactor: Float = 1f,
    overzoomEffect: OverzoomEffect = OverzoomEffect.RubberBanding,
  ) : this(
    maximum = ZoomLimit(maxZoomFactor, overzoomEffect),
    minimum = ZoomLimit(minZoomFactor, overzoomEffect),
  )

  @Deprecated(
    message = "Use OverZoomEffect instead",
    replaceWith = ReplaceWith("ZoomSpec(maxZoomFactor, overzoomEffect = TODO())"),
  )
  constructor(maxZoomFactor: Float, preventOverOrUnderZoom: Boolean) : this(
    maximum = ZoomLimit(
      factor = maxZoomFactor,
      overzoomEffect = if (preventOverOrUnderZoom) OverzoomEffect.RubberBanding else OverzoomEffect.NoLimits,
    )
  )

  @Deprecated("Use OverZoomEffect instead.")
  constructor(preventOverOrUnderZoom: Boolean) : this(
    maximum = ZoomLimit(
      factor = 2f,
      overzoomEffect = if (preventOverOrUnderZoom) OverzoomEffect.RubberBanding else OverzoomEffect.NoLimits,
    )
  )

  @Deprecated(
    message = "Use maximum.factor instead.",
    replaceWith = ReplaceWith("maximum.factor"),
  )
  val maxZoomFactor: Float
    get() = maximum.factor

  @Suppress("unused")
  @Deprecated(
    message = "Use maximum.overzoomEffect instead.",
    replaceWith = ReplaceWith("maximum.overzoomEffect != OverZoomEffect.None"),
  )
  val preventOverOrUnderZoom: Boolean
    get() = maximum.overzoomEffect != OverzoomEffect.NoLimits

  internal val range = ZoomRange(
    maxZoomAsRatioOfSize = maximum.factor,
    minZoomAsRatioOfBaseZoom = minimum.factor,
  )
}

@Poko
@Immutable
class ZoomLimit(
  /**
   * The zoom limit as a percentage of the content size before [overzoomEffect] kicks in.
   * For example, a value of `3.0` indicates that the content can be zoomed in up to 300%
   * of its original size.
   */
  val factor: Float,
  val overzoomEffect: OverzoomEffect = OverzoomEffect.RubberBanding,
)

/**
 * Represents a visual effect that displays when the zoom limits of a zoomable container
 * have been reached.
 *
 * TODO: Make OverzoomEffect extensible by consumers when ready.
 */
@Immutable
class OverzoomEffect internal constructor(
  private val value: Int
) {
  companion object {
    /**
     * Applies a rubber banding effect to zoom gestures when content is zoomed beyond
     * its limit as a form of visual feedback that the content can't be zoomed any further.
     */
    val RubberBanding: OverzoomEffect = OverzoomEffect(1)

    /**
     * Does not limit over/under zooms in any manner. Content will zoom in a free-form
     * manner even when it goes beyond its limit (until the gesture is released).
     */
    val NoLimits: OverzoomEffect = OverzoomEffect(2)

    /**
     * Disables overzoom effects entirely. Content will stop zooming as soon as it
     * reaches its limits, without any additional visual feedback or elasticity.
     */
    val Disabled: OverzoomEffect = OverzoomEffect(3)
  }

  override fun toString(): String {
    return when (value) {
      1 -> "OverzoomEffect.RubberBanding"
      2 -> "OverzoomEffect.NoLimits"
      3 -> "OverzoomEffect.Disabled"
      else -> super.toString()
    }
  }
}

internal fun ZoomSpec.copy(
  maximum: ZoomLimit = this.maximum,
  minimum: ZoomLimit = this.minimum,
): ZoomSpec = ZoomSpec(maximum, minimum)

internal fun ZoomLimit.copy(
  factor: Float = this.factor,
  overzoomEffect: OverzoomEffect = this.overzoomEffect,
) = ZoomLimit(factor, overzoomEffect)
