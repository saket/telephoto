package me.saket.telephoto.subsamplingimage

import java.io.IOException

// todo: docs.
interface SubSamplingImageErrorReporter {
  // todo: docs.
  fun onImageLoadingFailed(e: IOException) = Unit

  companion object {
    val NoOp = object : SubSamplingImageErrorReporter {}
  }
}
