package me.saket.telephoto.subsamplingimage

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach
import me.saket.telephoto.subsamplingimage.internal.discardFractionalParts
import me.saket.telephoto.subsamplingimage.internal.toCeilInt

@Composable
fun SubSamplingImage(
  state: SubSamplingImageState,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current

  val onDraw: DrawScope.() -> Unit = {
    state.visibleTiles.fastForEach { tile ->
      if (tile.isVisible && tile.bitmap != null) {
        drawImage(
          image = tile.bitmap,
          srcOffset = IntOffset.Zero,
          srcSize = IntSize(tile.bitmap.width, tile.bitmap.height),
          dstOffset = tile.drawBounds.topLeft.discardFractionalParts(),
          dstSize = tile.drawBounds.size.discardFractionalParts(),
        )
      }

      if (SubSamplingImageState.showTileBounds) {
        drawRect(
          color = Color.White,
          topLeft = tile.drawBounds.topLeft,
          size = tile.drawBounds.size,
          style = Stroke(width = density.run { 2.dp.toPx() })
        )
      }
    }
  }

  Spacer(
    modifier
      .onSizeChanged { state.canvasSize = it.toSize() }
      .drawBehind(onDraw)
      .wrapContentSizeIfNeeded(state.imageSize)
  )
}

@Suppress("NAME_SHADOWING")
private fun Modifier.wrapContentSizeIfNeeded(imageSize: Size): Modifier {
  if (imageSize.isUnspecified) {
    return Modifier
  }

  return layout { measurable, constraints ->
    val constraints = if (!constraints.hasFixedSize) {
      val scaleToFitImage = minOf(
        constraints.maxWidth / imageSize.width,
        constraints.maxHeight / imageSize.height
      )
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

