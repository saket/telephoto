package me.saket.telephoto.zoomable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun ColorLegend(
  colorsToNames: ImmutableList<Pair<Color, String>>,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    colorsToNames.fastForEach { (color, name) ->
      Row(
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          Modifier
            .size(16.dp)
            .background(color, CircleShape)
        )

        BasicText(
          text = name,
          color = { Color.White },
        )
      }
    }
  }
}
