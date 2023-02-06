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
      val transformedBounds = tile.bounds.let {
        it.copy(
          left = (it.left * state.scale.scaleX) + state.translation.x,
          right = (it.right * state.scale.scaleX) + state.translation.x,
          top = (it.top * state.scale.scaleY) + state.translation.y,
          bottom = (it.bottom * state.scale.scaleY) + state.translation.y,
        )
      }

      drawRect(
        color = Color.Yellow,
        topLeft = transformedBounds.topLeft,
        size = transformedBounds.size,
        style = Stroke(width = density.run { 1.dp.toPx() })
      )
    }
  }
}
