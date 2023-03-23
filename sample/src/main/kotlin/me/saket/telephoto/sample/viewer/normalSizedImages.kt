package me.saket.telephoto.sample.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import me.saket.telephoto.Image
import me.saket.telephoto.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableViewportState

@Composable
fun NormalSizedRemoteImage(
  viewportState: ZoomableViewportState
) {
  Image(
    imageSource = ZoomableImageSource.coil(
      ImageRequest.Builder(LocalContext.current)
        .data("https://images.unsplash.com/photo-1674560109079-0b1cd708cc2d?w=1500")
        .build()
    ),
    zoomState = viewportState,
    contentDescription = null,
  )
}

@Composable
fun NormalSizedLocalImage(
  viewportState: ZoomableViewportState,
) {
  //  Image(
  //    painter = painterResource(R.drawable.fox_smol),
  //    viewportState = viewportState,
  //    contentDescription = null,
  //  )

  Image(
    assetName = "path.jpg",
    zoomState = viewportState,
    contentDescription = null
  )
}
