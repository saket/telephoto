package me.saket.telephoto.subsamplingimage

import androidx.compose.ui.geometry.Size
import java.io.IOException

// todo: docs.
interface SubSamplingImageEventListener {
  // todo: find a better name because "loaded" sounds similar to "displayed".
  fun onImageLoaded(imageSize: Size) = Unit

  // todo: docs.
  fun onImageLoadingFailed(e: IOException) = Unit

  // todo: docs.
  fun onImageDisplayed() = Unit

  companion object {
    val Empty = object : SubSamplingImageEventListener {}
  }
}
