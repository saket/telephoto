package me.saket.telephoto.subsamplingimage

import java.io.IOException

// todo: docs.
interface SubSamplingImageErrorReporter {
  // todo: docs.
  fun onImageLoadingFailed(e: IOException, imageSource: ImageSource) = Unit

  companion object {
    val NoOpInRelease = object : SubSamplingImageErrorReporter {
      override fun onImageLoadingFailed(e: IOException, imageSource: ImageSource) {
        if (BuildConfig.DEBUG) {
          // I'm not entirely convinced with this, but I think failure
          // in loading of bitmaps from local storage is not a good sign
          // and should be surfaced asap in debug builds.
          throw e
        }
      }
    }
  }
}
