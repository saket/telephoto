package me.saket.telephoto.sample.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import me.saket.telephoto.AsyncImageSource
import me.saket.telephoto.AsyncImage
import me.saket.telephoto.zoomable.ZoomableViewportState

@Composable
fun LargeImage(viewportState: ZoomableViewportState) {
  // TODO: handle errors here.
  // TODO: show loading.
  AsyncImage(
    imageSource = AsyncImageSource.coil(
      ImageRequest.Builder(LocalContext.current)
        .data("https://images.unsplash.com/photo-1678465952838-c9d7f5daaa65")
        .build()
    ),
    viewportState = viewportState,
    contentDescription = null,
  )
}
