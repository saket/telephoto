package me.saket.telephoto.sample.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.saket.telephoto.sample.R
import me.saket.telephoto.zoomable.ZoomableViewportState
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage

@Composable
fun NormalSizedLocalImage(
  viewportState: ZoomableViewportState,
) {
  ZoomableAsyncImage(
    modifier = Modifier.fillMaxSize(),
    model = R.drawable.fox_smol,
    state = viewportState,
    contentDescription = null,
  )
}

@Composable
fun NormalSizedRemoteImage(
  viewportState: ZoomableViewportState
) {
  ZoomableAsyncImage(
    model = "https://images.unsplash.com/photo-1674560109079-0b1cd708cc2d?w=1500",
    state = viewportState,
    contentDescription = null,
  )
}

@Composable
fun LargeImage(
  viewportState: ZoomableViewportState
) {
  // TODO: handle errors here.
  // TODO: show loading.

  ZoomableAsyncImage(
    modifier = Modifier.fillMaxSize(),
    model = "https://images.unsplash.com/photo-1678465952838-c9d7f5daaa65",
    state = viewportState,
    contentDescription = null,
  )
}
