package me.saket.telephoto.zoomable

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

@Stable
internal fun Painter.withFixedSize(size: Size): Painter {
  return ForwardingPainter(
    painter = this,
    overriddenSize = size.takeOrElse { intrinsicSize },
  )
}

/**
 * Copied from [Coil](https://gist.github.com/colinrtwhite/c2966e0b8584b4cdf0a5b05786b20ae1).
 */
private class ForwardingPainter(
  private val painter: Painter,
  private val overriddenSize: Size = painter.intrinsicSize,
) : Painter() {

  private var alpha: Float = DefaultAlpha
  private var colorFilter: ColorFilter? = null

  override val intrinsicSize get() = overriddenSize

  override fun applyAlpha(alpha: Float): Boolean {
    this.alpha = alpha
    return true
  }

  override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
    this.colorFilter = colorFilter
    return true
  }

  override fun DrawScope.onDraw() {
    with(painter) {
      draw(size, alpha, colorFilter)
    }
  }
}
