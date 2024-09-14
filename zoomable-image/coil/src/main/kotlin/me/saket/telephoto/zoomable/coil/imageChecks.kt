@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.zoomable.coil

import android.util.TypedValue
import coil.decode.DecodeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.ResourceImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import okio.Buffer
import okio.Source
import okio.buffer

context(Resolver)
internal suspend fun SubSamplingImageSource.canBeSubSampled(): Boolean {
  return withContext(Dispatchers.IO) {
    when (this@canBeSubSampled) {
      is ResourceImageSource -> !isVectorDrawable()
      else -> canBeSubSampled(peek(request.context))
    }
  }
}

context(Resolver)
private fun ResourceImageSource.isVectorDrawable(): Boolean =
  TypedValue().apply {
    request.context.resources.getValue(id, this, /* resolveRefs = */ true)
  }.string.endsWith(".xml")

private fun canBeSubSampled(source: Source): Boolean {
  return source.buffer().use {
    // Check for GIFs as well because Android's ImageDecoder can return a Bitmap for single-frame GIFs.
    !DecodeUtils.isSvg(it) && !DecodeUtils.isGif(it)
  }
}

context(Resolver)
internal suspend fun SubSamplingImageSource.exists(): Boolean {
  return withContext(Dispatchers.IO) {
    try {
      peek(request.context).read(Buffer(), byteCount = 1) != -1L
    } catch (e: okio.FileNotFoundException) {
      false
    }
  }
}
