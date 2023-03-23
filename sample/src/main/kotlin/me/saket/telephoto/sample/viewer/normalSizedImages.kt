package me.saket.telephoto.sample.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.request.ImageRequest
import me.saket.telephoto.AsyncImage
import me.saket.telephoto.AsyncImageSource
import me.saket.telephoto.Image
import me.saket.telephoto.sample.R
import me.saket.telephoto.zoomable.ZoomableViewportState

@Composable
fun NormalSizedRemoteImage(
  viewportState: ZoomableViewportState
) {
  AsyncImage(
    imageSource = AsyncImageSource.coil(
      ImageRequest.Builder(LocalContext.current)
        .data("https://images.unsplash.com/photo-1674560109079-0b1cd708cc2d?w=1500")
        .build()
    ),
    viewportState = viewportState,
    contentDescription = null,
  )
}

@Composable
fun NormalSizedLocalImage(
  viewportState: ZoomableViewportState,
) {
  Image(
    painter = painterResource(R.drawable.fox_smol),
    viewportState = viewportState,
    contentDescription = null,
  )
}
