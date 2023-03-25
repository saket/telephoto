package me.saket.telephoto.zoomable.coil

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import me.saket.telephoto.zoomable.Image
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableViewportState

// todo: doc.
@Composable
fun Image(
  zoomablePainter: Painter,
  viewportState: ZoomableViewportState,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
) {
  Image(
    zoomableImage = ZoomableImageSource.painter(zoomablePainter),
    viewportState = viewportState,
    contentDescription = contentDescription,
    modifier = modifier,
    alpha = alpha,
    colorFilter = colorFilter,
  )
}

// todo: doc.
@Composable
fun Image(
  zoomableAsset: String,
  viewportState: ZoomableViewportState,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
) {
  Image(
    zoomableImage = ZoomableImageSource.asset(zoomableAsset),
    viewportState = viewportState,
    contentDescription = contentDescription,
    modifier = modifier,
    alpha = alpha,
    colorFilter = colorFilter,
  )
}
