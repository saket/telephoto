package me.saket.telephoto.subsamplingimage

import androidx.compose.ui.geometry.Size
import java.io.IOException

// todo: docs.
interface SubSamplingImageEventListener {
  fun onImageLoaded(imageSize: Size) = Unit
  fun onImageLoadingFailed(e: IOException) = Unit

  fun onImageDisplayed() = Unit

  fun onTileLoaded() = Unit
  fun onTileLoadingFailed() = Unit

  companion object {
    val Empty = object : SubSamplingImageEventListener {}
  }
}
