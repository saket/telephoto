package me.saket.telephoto.subsamplingimage

import java.io.IOException

interface SubSamplingImageErrorReporter {

  /**
   * Called when loading of an [imageSource] fails. This can happen when a decoder can't be
   * created for the image, or when one of its regions can't be decoded. In the latter case,
   * this is called once per image and the failed regions are left blank (or covered by the
   * image's preview or low-res base tile, if available).
   */
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
