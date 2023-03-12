package me.saket.telephoto.sample.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.viewport.ZoomableViewportState

@Composable
fun SubSampledImage(viewportState: ZoomableViewportState) {
  SubSamplingImage(
    modifier = Modifier.fillMaxSize(),
    state = rememberSubSamplingImageState(
      image = ImageSource.asset("path.jpg"),
      viewportState = viewportState,
    ),
    contentDescription = null,
  )
}
