package me.saket.telephoto.sample.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil.compose.rememberAsyncImagePainter
import me.saket.telephoto.sample.R
import me.saket.telephoto.zoomable.Image
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.ZoomableViewportState
import me.saket.telephoto.zoomable.coil.coil

@Composable
fun NormalSizedLocalImage(
  viewportState: ZoomableViewportState,
) {
  Image(
    modifier = Modifier.fillMaxSize(),
    zoomableImage = ZoomableImage.coil(rememberAsyncImagePainter(R.drawable.fox_smol)),
    viewportState = viewportState,
    contentDescription = null,
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
