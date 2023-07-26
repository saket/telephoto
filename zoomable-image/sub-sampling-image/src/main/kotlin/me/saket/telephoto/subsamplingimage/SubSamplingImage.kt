package me.saket.telephoto.subsamplingimage

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach
import me.saket.telephoto.subsamplingimage.internal.createRotationMatrix
import me.saket.telephoto.subsamplingimage.internal.toCeilInt

/**
 * An Image composable that can render large bitmaps by diving them into tiles so that they
 * can be loaded lazily. This ensures that images maintain their intricate details even when
 * fully zoomed in, without causing any `OutOfMemory` exceptions.
 *
 * [SubSamplingImage] is automatically used by [ZoomableImage][me.saket.telephoto.zoomable.ZoomableImage].
 */
@Composable
fun SubSamplingImage(
  state: SubSamplingImageState,
  modifier: Modifier = Modifier,
  contentDescription: String?,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
) {
  val paint = remember { Paint() }.also {
    it.alpha = alpha
    it.colorFilter = colorFilter
  }
  val onDraw: DrawScope.() -> Unit = {
    state.tiles.fastForEach { tile ->
      if (tile.bitmap != null && state.isImageLoaded) {
        drawIntoCanvas {
          it.nativeCanvas.drawBitmap(
            tile.bitmap.asAndroidBitmap(),
            tile.createRotationMatrix(),
            paint.asFrameworkPaint(),
          )
        }
      }

      if (state.showTileBounds) {
        drawRect(
          color = Color.Black,
          topLeft = tile.bounds.topLeft.toOffset(),
          size = tile.bounds.size.toSize(),
          style = Stroke(width = 2.dp.toPx())
        )
      }
    }
  }

  Box(
    modifier
      .contentDescription(contentDescription)
      .onSizeChanged { state.canvasSize = it }
      .drawBehind(onDraw)
      .wrapContentSizeIfNeeded(state.imageSize)
  )
}

@Stable
private fun Modifier.contentDescription(contentDescription: String?): Modifier {
  if (contentDescription != null) {
    return semantics {
      this.contentDescription = contentDescription
      this.role = Role.Image
    }
  } else {
    return this
  }
}

@Stable
@Suppress("NAME_SHADOWING")
private fun Modifier.wrapContentSizeIfNeeded(imageSize: IntSize?): Modifier {
  if (imageSize == null) {
    return this
  }

  return layout { measurable, constraints ->
    val constraints = if (!constraints.hasFixedSize) {
      val scaleToFitImage = minOf(
        constraints.maxWidth / imageSize.width.toFloat(),
        constraints.maxHeight / imageSize.height.toFloat()
      ).coerceAtMost(1f)
      constraints.constrain(
        Constraints(
          minWidth = (scaleToFitImage * imageSize.width).toCeilInt(),
          minHeight = (scaleToFitImage * imageSize.height).toCeilInt()
        )
      )
    } else {
      constraints
    }
    val placeable = measurable.measure(constraints)
    layout(width = placeable.width, height = placeable.height) {
      placeable.place(IntOffset.Zero)
    }
  }
}

@Stable
private val Constraints.hasFixedSize
  get() = hasFixedWidth && hasFixedHeight
