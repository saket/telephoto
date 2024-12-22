package me.saket.telephoto.subsamplingimage.internal

import android.content.Context
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.util.canBeSubSampled as canBeSubSampledV2
import me.saket.telephoto.subsamplingimage.util.exists as existsV2

/**
 * Check whether an image source can be sub-sampled and decoded using [AndroidImageRegionDecoder].
 */
@Deprecated(
  message = "Moved to another package",
  replaceWith = ReplaceWith("canBeSubSampled(context)", "me.saket.telephoto.subsamplingimage.util.canBeSubSampled")
)
suspend fun SubSamplingImageSource.canBeSubSampled(context: Context): Boolean {
  return canBeSubSampledV2(context)
}

/** Check whether an image source exists and has non-zero bytes. */
@Deprecated(
  message = "Moved to another package",
  replaceWith = ReplaceWith("exists(context)", "me.saket.telephoto.subsamplingimage.util.exists")
)
suspend fun SubSamplingImageSource.exists(context: Context): Boolean {
  return existsV2(context)
}
