package me.saket.telephoto.subsamplingimage

import java.io.IOException

interface SubSamplingImageErrorReporter {

  /** Called when loading of an [imageSource] fails. */
  fun onImageLoadingFailed(e: IOException, imageSource: SubSamplingImageSource) = Unit

  companion object {
    val NoOpInRelease = object : SubSamplingImageErrorReporter {
      override fun onImageLoadingFailed(e: IOException, imageSource: SubSamplingImageSource) {
        if (BuildConfig.DEBUG) {
          // I'm not entirely convinced with this, but I think failure in loading of bitmaps from
          // local storage is not a good sign and should be surfaced ASAP in debug builds. Please
          // file an issue on https://github.com/saket/telephoto/issues if you think otherwise.
          throw e
        }
      }
    }
  }
}
