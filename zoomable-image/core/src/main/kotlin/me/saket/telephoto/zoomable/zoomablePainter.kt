package me.saket.telephoto.zoomable

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter

@Composable
internal fun zoomablePainter(painter: Painter, transformation: ZoomableContentTransformation): Painter {
  return ForwardingPainter(
    painter = painter,
    // Overriding the original size is useful in two ways:
    // 1. When this painter is used as a placeholder for a higher quality image, its size is
    //    matched with that of the full quality one. This ensures that the zoom & pan values are
    //    retained when this placeholder is later swapped out with the full quality image.
    //
    // 2. This painter's size can go out of sync with the size reported to ZoomableState
    //    because the reporting is done asynchronously. Locking its size avoids UI flickers.
    overriddenSize = transformation.contentSize,
    drawWithContent = { drawContent ->
      translate(
        left = transformation.offset.x,
        top = transformation.offset.y,
      ) {
        scale(
          scaleX = transformation.scale.scaleX,
          scaleY = transformation.scale.scaleY,
          pivot = transformation.transformOrigin.toOffset(),
        ) {
          drawContent()
        }
      }
    }
  )
}

context(DrawScope)
private fun TransformOrigin.toOffset(): Offset {
  return Offset(
    x = pivotFractionX * size.width,
    y = pivotFractionY * size.height,
  )
}

/**
 * Copied from [Coil](https://gist.github.com/colinrtwhite/c2966e0b8584b4cdf0a5b05786b20ae1).
 */
private class ForwardingPainter(
  private val painter: Painter,
  private val overriddenSize: Size = painter.intrinsicSize,
  private val drawWithContent: DrawScope.(drawContent: () -> Unit) -> Unit,
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
    drawWithContent {
      painter.run {
        draw(size, alpha, colorFilter)
      }
    }
  }
}
