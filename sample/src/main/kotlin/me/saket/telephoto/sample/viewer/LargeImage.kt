package me.saket.telephoto.sample.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil.compose.rememberAsyncImagePainter
import me.saket.telephoto.zoomable.Image
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.ZoomableViewportState
import me.saket.telephoto.zoomable.coil.coil

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
