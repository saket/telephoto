package me.saket.telephoto.zoomable

data class ZoomSpec(
  /**
   * The maximum zoom level as a percentage of the content size before rubber banding kicks in.
   *
   * For example, a value of `3.0` indicates that the content can be zoomed in up to 300%
   * of its original size. Setting this value to `1.0` or less will disable zooming.
   */
  val maxZoomFactor: Float = 2f,

  /**
   * Whether to apply rubber banding to zoom gestures when content is over or under zoomed
   * as a form of visual feedback that the content can't be zoomed any further. When set to false,
   * content will keep zooming in a free-form manner even when it goes beyond its boundaries.
   */
  val preventOverOrUnderZoom: Boolean = true,
) {
  internal val range = ZoomRange(maxZoomAsRatioOfSize = maxZoomFactor)
}
