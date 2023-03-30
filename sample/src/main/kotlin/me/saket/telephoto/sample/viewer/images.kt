package me.saket.telephoto.sample.viewer

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import me.saket.telephoto.sample.R
import me.saket.telephoto.zoomable.Image
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.ZoomableViewportState
import me.saket.telephoto.zoomable.applyTransformation
import me.saket.telephoto.zoomable.coil.coil
import me.saket.telephoto.zoomable.zoomable

@Composable
fun NormalSizedLocalImage(
  viewportState: ZoomableViewportState,
) {
  val painter = rememberAsyncImagePainter(
    "https://images.unsplash.com/photo-1674560109079-0b1cd708cc2d?w=1500"
  )

  LaunchedEffect(painter.intrinsicSize) {
    viewportState.setContentLocation(
      ZoomableContentLocation.relative(
        size = painter.intrinsicSize,
        contentScale = ContentScale.Inside,
        alignment = Alignment.TopCenter,
      )
    )
  }

  androidx.compose.foundation.Image(
    modifier = Modifier
      .fillMaxSize()
      .border(1.dp, Color.Yellow)
      .zoomable(viewportState)
      .applyTransformation(viewportState.contentTransformation),
    painter = painter,
    contentDescription = null,
    alignment = Alignment.TopCenter,
    contentScale = ContentScale.Inside,
  )
}

@Composable
fun NormalSizedRemoteImage(
  viewportState: ZoomableViewportState
) {
  Image(
    zoomableImage = ZoomableImage.coil(
      rememberAsyncImagePainter("https://images.unsplash.com/photo-1674560109079-0b1cd708cc2d?w=1500")
    ),
    viewportState = viewportState,
    contentDescription = null,
  )
}

@Composable
fun LargeImage(
  viewportState: ZoomableViewportState
) {
  // TODO: handle errors here.
  // TODO: show loading.

  Image(
    modifier = Modifier.fillMaxSize(),
    zoomableImage = ZoomableImage.coil(
      rememberAsyncImagePainter("https://images.unsplash.com/photo-1678465952838-c9d7f5daaa65")
    ),
    viewportState = viewportState,
    contentDescription = null,
  )
}
