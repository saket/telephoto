package me.saket.telephoto.subsamplingimage.util

import android.content.Context
import android.util.TypedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.ResourceImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.internal.AndroidImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.isGif
import me.saket.telephoto.subsamplingimage.internal.isSvg
import okio.Buffer

/**
 * Check whether an image source can be sub-sampled and decoded using [AndroidImageRegionDecoder].
 */
suspend fun SubSamplingImageSource.canBeSubSampled(context: Context): Boolean {
  if (this is ResourceImageSource) {
    return !isVectorDrawable(context)
  }

  return withContext(Dispatchers.IO) {
    peek(context).use {
      // Check for GIFs as well because Android's ImageDecoder
      // can return a Bitmap for single-frame GIFs.
      !isSvg(it) && !isGif(it)
    }
  }
}

/** Check whether an image source exists and has non-zero bytes. */
suspend fun SubSamplingImageSource.exists(context: Context): Boolean {
  return withContext(Dispatchers.IO) {
    try {
      peek(context).read(Buffer(), byteCount = 1) != -1L
    } catch (e: okio.FileNotFoundException) {
      // This catch block currently makes an assumption that files are only read
      // using okio, which is true for SubSamplingImageSource.file(), but might
      // fail for SubSamplingImageSource.rawSource(). I could probably make exists()
      // a member function of SubSamplingImageSource to fix that.
      false
    }
  }
}

private suspend fun ResourceImageSource.isVectorDrawable(context: Context): Boolean {
  return withContext(Dispatchers.IO) {
    TypedValue().apply {
      context.resources.getValue(id, this, /* resolveRefs = */ true)
    }.string.endsWith(".xml")
  }
}
