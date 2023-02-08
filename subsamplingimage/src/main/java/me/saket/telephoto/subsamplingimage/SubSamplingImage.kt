package me.saket.telephoto.subsamplingimage

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach

@Composable
fun SubSamplingImage(
  state: SubSamplingImageState,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current

  Canvas(
    modifier.onSizeChanged { state.canvasSize = it.toSize() }
  ) {
    state.visibleTiles.fastForEach { tile ->
      if (tile.isVisible && tile.bitmap != null) {
        drawImage(
          image = tile.bitmap.asImageBitmap(),
          srcOffset = IntOffset.Zero,
          srcSize = IntSize(tile.bitmap.width, tile.bitmap.height),
          dstOffset = tile.drawBounds.topLeft.toIntOffset(),
          dstSize = tile.drawBounds.size.toIntSize(),
        )
      }

      if (debugTiles) {
        drawRect(
          color = Color(0xFF292A30),
          topLeft = tile.drawBounds.topLeft,
          size = tile.drawBounds.size,
          style = Stroke(width = density.run { 2.dp.toPx() })
        )
      }
    }
  }
}

private const val debugTiles = true

private fun Size.toIntSize(): IntSize {
  return IntSize(width.toInt(), height.toInt())
}

private fun Offset.toIntOffset(): IntOffset {
  return IntOffset(x.toInt(), y.toInt())
}
