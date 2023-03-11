@file:OptIn(ExperimentalMaterial3Api::class)

package me.saket.telephoto.sample.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.saket.telephoto.sample.R
import me.saket.telephoto.viewport.ZoomableContentLocation
import me.saket.telephoto.viewport.ZoomableViewportState
import me.saket.telephoto.viewport.graphicsLayer

@Composable
fun NormalSizedRemoteImage(
  viewportState: ZoomableViewportState
) {
  AsyncImage(
    modifier = Modifier
      .graphicsLayer(viewportState.contentTransformation)
      .fillMaxSize(),
    model = ImageRequest.Builder(LocalContext.current)
      .data("https://images.unsplash.com/photo-1674560109079-0b1cd708cc2d?w=1500")
      .crossfade(true)
      .build(),
    contentDescription = null,
    onState = {
      viewportState.setContentLocation(
        ZoomableContentLocation.fitInsideAndCenterAligned(it.painter?.intrinsicSize)
      )
    }
  )
}

@Composable
fun NormalSizedLocalImage(
  viewportState: ZoomableViewportState
) {
  val painter = painterResource(R.drawable.fox_smol)
  LaunchedEffect(painter) {
    viewportState.setContentLocation(
      ZoomableContentLocation.fitInsideAndCenterAligned(painter.intrinsicSize)
    )
  }

  Image(
    modifier = Modifier
      .fillMaxSize()
      .graphicsLayer(viewportState.contentTransformation),
    painter = painter,
    contentScale = ContentScale.Inside,
    contentDescription = null
  )
}
