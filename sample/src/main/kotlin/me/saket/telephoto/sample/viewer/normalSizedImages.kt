package me.saket.telephoto.sample.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import coil.compose.rememberAsyncImagePainter
import me.saket.telephoto.sample.R
import me.saket.telephoto.zoomable.Image
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableViewportState
import me.saket.telephoto.zoomable.coil.painter

@Composable
fun NormalSizedLocalImage(
  viewportState: ZoomableViewportState,
) {
  Image(
    modifier = Modifier.fillMaxSize(),
    zoomableImage = ZoomableImageSource.painter(painterResource(R.drawable.fox_smol)),
    viewportState = viewportState,
    contentDescription = null,
  )
}

@Composable
fun NormalSizedRemoteImage(
  viewportState: ZoomableViewportState
) {
  Image(
    modifier = Modifier.fillMaxSize(),
    zoomableImage = ZoomableImageSource.painter(
      rememberAsyncImagePainter("https://images.unsplash.com/photo-1674560109079-0b1cd708cc2d?w=1500")
    ),
    viewportState = viewportState,
    contentDescription = null,
  )
}
