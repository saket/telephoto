package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.drewhamilton.poko.Poko
import kotlin.jvm.JvmInline

@Immutable
fun interface DynamicZoomSpec {
  /**
   * Lazily computes a [ZoomSpec] based on the layout and content size.
   * Called once every time its inputs change.
   */
  fun DynamicZoomSpecScope.compute(inputs: DynamicZoomSpecInputs): ZoomSpec

  companion object {
    /** Applies recommended adjustments to [zoomSpec], if necessary. */
    fun recommend(zoomSpec: ZoomSpec): DynamicZoomSpec =
      RecommendedDynamicZoomSpec(zoomSpec)
  }
}

@Immutable
interface DynamicZoomSpecScope

@Poko
@Immutable
class DynamicZoomSpecInputs internal constructor(
  /** The original, unscaled size of the content reported by [ZoomableState.setContentLocation]. */
  val unscaledContentSize: Size,

  /**
   * The visual bounds of the content. This is calculated by applying [contentScale][ZoomableState.contentScale]
   * and [contentAlignment][ZoomableState.contentAlignment] to the value passed to [ZoomableState.setContentLocation].
   * Does not include any user zoom or pan.
   */
  val scaledContentBounds: Rect,

  /** Bounds of the viewport minus any [contentPadding][ZoomableState.contentPadding]. */
  val paddedViewportBounds: Rect,
)

@JvmInline
private value class RecommendedDynamicZoomSpec(val delegate: ZoomSpec) : DynamicZoomSpec {
  override fun DynamicZoomSpecScope.compute(inputs: DynamicZoomSpecInputs): ZoomSpec {
    val initialScale = inputs.scaledContentBounds.size.maxDimension / inputs.unscaledContentSize.maxDimension
    return if (initialScale > 1f) {
      // If the content is initially displayed larger than its original size to fill the
      // viewport (based on its content scale), it might not be zoomable because its initial
      // scale already exceeds the max zoom. To solve this, shift the max zoom limit by treating
      // the viewport size as its new relative base. https://github.com/saket/telephoto/issues/45.
      delegate.copy(
        maximum = delegate.maximum.copy(
          factor = delegate.maximum.factor * initialScale,
        )
      )
    } else {
      delegate
    }
  }
}

internal data object RealDynamicZoomSpecScope : DynamicZoomSpecScope
