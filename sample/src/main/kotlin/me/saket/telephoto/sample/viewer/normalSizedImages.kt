package me.saket.telephoto.sample.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import me.saket.telephoto.sample.R
import me.saket.telephoto.zoomable.Image
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableViewportState
import me.saket.telephoto.zoomable.coil.Image
import me.saket.telephoto.zoomable.coil.coil

@Composable
fun NormalSizedLocalImage(
  viewportState: ZoomableViewportState,
) {
  if (false) {
    Image(
      modifier = Modifier.fillMaxSize(),
      zoomablePainter = rememberAsyncImagePainter(R.drawable.fox_smol),
      viewportState = viewportState,
      contentDescription = null,
    )
  } else {
    Image(
      modifier = Modifier.fillMaxSize(),
      zoomableAsset = "path.jpg",
      viewportState = viewportState,
      contentDescription = null
    )
  }
}

@Composable
fun NormalSizedRemoteImage(
  viewportState: ZoomableViewportState
) {
  Image(
    modifier = Modifier.fillMaxSize(),
    zoomableImage = ZoomableImageSource.coil(
      ImageRequest.Builder(LocalContext.current)
        .data("https://images.unsplash.com/photo-1674560109079-0b1cd708cc2d?w=1500")
        .build()
    ),
    viewportState = viewportState,
    contentDescription = null,
  )
}
