package me.saket.telephoto.zoomable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
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
   * Whether the image is loaded and displayed (not necessarily in its full quality).
   * This will be `false` for placeholders/thumbnails, where [isPlaceholderDisplayed] can be used instead.
   */
  var isImageDisplayed: Boolean by mutableStateOf(false)
    internal set

  /**
   * Whether the image is loaded and displayed in its full quality.
   * This be `false` for placeholders/thumbnails, where [isPlaceholderDisplayed] can be used instead.
   **/
  val isImageDisplayedInFullQuality: Boolean by derivedStateOf {
    isImageDisplayed && subSamplingState.let { it == null || it.isImageDisplayedInFullQuality }
  }

  /**
   * Whether a preview of the image is displayed, during
   * which zoom & pan interactions will remain disabled.
   */
  var isPlaceholderDisplayed: Boolean by mutableStateOf(false)
    internal set

  /**
   * Available only when the image was lazy-loaded using
   * [SubSamplingImage()][me.saket.telephoto.subsamplingimage.SubSamplingImage].
   */
  internal var subSamplingState: SubSamplingImageState? by mutableStateOf(null)
}
