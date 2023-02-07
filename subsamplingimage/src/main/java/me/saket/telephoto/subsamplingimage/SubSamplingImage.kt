package me.saket.telephoto.subsamplingimage

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
      drawRect(
        color = Color(0xFF3E9B42).copy(alpha = if (tile.isVisible) 1f else 0.1f),
        topLeft = tile.bounds.topLeft,
        size = tile.bounds.size,
      )
      drawRect(
        color = Color(0xFF292A30),
        topLeft = tile.bounds.topLeft,
        size = tile.bounds.size,
        style = Stroke(width = density.run { 2.dp.toPx() })
      )
    }
  }
}
