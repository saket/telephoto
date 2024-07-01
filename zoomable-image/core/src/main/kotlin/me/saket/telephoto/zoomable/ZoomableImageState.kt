package me.saket.telephoto.zoomable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.saket.telephoto.subsamplingimage.SubSamplingImageState

/** State for [ZoomableImageSource]. */
@Composable
fun rememberZoomableImageState(
  zoomableState: ZoomableState = rememberZoomableState()
): ZoomableImageState {
  return remember(zoomableState) {
    ZoomableImageState(zoomableState)
  }
}

@Stable
class ZoomableImageState internal constructor(
  val zoomableState: ZoomableState
) {
  /**
   * Whether the full quality image is loaded. This be false for
   * placeholders/thumbnails, where [isPlaceholderDisplayed] can be used instead.
   */
  var isImageDisplayed: Boolean by mutableStateOf(false)
    internal set

  var isPlaceholderDisplayed: Boolean by mutableStateOf(false)
    internal set

  /**
   * Available when the image was lazy-loaded using
   * [SubSamplingImage()][me.saket.telephoto.subsamplingimage.SubSamplingImage].
   */
  internal var subSamplingState: SubSamplingImageState? by mutableStateOf(null)
}
