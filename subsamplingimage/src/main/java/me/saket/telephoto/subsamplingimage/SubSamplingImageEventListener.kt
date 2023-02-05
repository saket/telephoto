package me.saket.telephoto.subsamplingimage

import java.io.IOException

// todo: docs.
interface SubSamplingImageEventListener {
  fun onImageLoaded() = Unit
  fun onImageLoadingFailed(e: IOException) = Unit

  fun onImageDisplayed() = Unit

  fun onTileLoaded() = Unit
  fun onTileLoadingFailed() = Unit

  companion object {
    val Empty = object : SubSamplingImageEventListener {}
  }
}
