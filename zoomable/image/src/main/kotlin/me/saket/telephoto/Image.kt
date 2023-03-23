package me.saket.telephoto

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomableViewportState
import me.saket.telephoto.zoomable.applyTransformation

@Composable
fun Image(
  painter: Painter,
  viewportState: ZoomableViewportState,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null
) {
  LaunchedEffect(painter.intrinsicSize) {
    viewportState.setContentLocation(
      ZoomableContentLocation.fitInsideAndCenterAligned(painter.intrinsicSize)
    )
  }

  Image(
    modifier = modifier
      .fillMaxSize()
      .applyTransformation(viewportState.contentTransformation),
    painter = painter,
    contentDescription = contentDescription,
    alignment = Alignment.Center,
    contentScale = ContentScale.Inside,
    alpha = alpha,
    colorFilter = colorFilter,
  )
}
