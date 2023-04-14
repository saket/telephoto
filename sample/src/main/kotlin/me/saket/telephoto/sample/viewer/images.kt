package me.saket.telephoto.sample.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import me.saket.telephoto.sample.R
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState

@Composable
fun NormalSizedLocalImage(
  zoomableState: ZoomableState,
) {
  ZoomableAsyncImage(
    modifier = Modifier.fillMaxSize(),
    model = R.drawable.fox_smol,
    state = rememberZoomableImageState(zoomableState),
    contentDescription = null,
  )
}

@Composable
fun NormalSizedRemoteImage(
  zoomableState: ZoomableState
) {
  ZoomableAsyncImage(
    modifier = Modifier.fillMaxSize(),
    model = ImageRequest.Builder(LocalContext.current)
      .data("https://images.unsplash.com/photo-1678465952838-c9d7f5daaa65?w=100")
      .memoryCacheKey("placeholder")
      .build(),
    state = rememberZoomableImageState(zoomableState),
    contentDescription = null,
  )
}

@Composable
fun LargeImage(
  zoomableState: ZoomableState
) {
  // TODO: handle errors here.
  // TODO: show loading.

  ZoomableAsyncImage(
    modifier = Modifier.fillMaxSize(),
    model = ImageRequest.Builder(LocalContext.current)
      .data("https://images.unsplash.com/photo-1678465952838-c9d7f5daaa65")
      .placeholderMemoryCacheKey("placeholder")
      .crossfade(1_000)
      .build(),
    state = rememberZoomableImageState(zoomableState),
    contentDescription = null,
  )
}
