@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.zoomable.coil

import android.annotation.SuppressLint
import android.util.TypedValue
import coil.decode.DecodeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.AssetImageSource
import me.saket.telephoto.subsamplingimage.FileImageSource
import me.saket.telephoto.subsamplingimage.RawImageSource
import me.saket.telephoto.subsamplingimage.ResourceImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.UriImageSource
import okio.Buffer
import okio.FileSystem
import okio.Source
import okio.buffer
import okio.source

context(Resolver)
internal suspend fun SubSamplingImageSource.canBeSubSampled(): Boolean {
  return withContext(Dispatchers.IO) {
    when (this@canBeSubSampled) {
      is ResourceImageSource -> !isVectorDrawable()
      is AssetImageSource -> canBeSubSampled()
      is UriImageSource -> canBeSubSampled()
      is FileImageSource -> canBeSubSampled(FileSystem.SYSTEM.source(path))
      is RawImageSource -> canBeSubSampled(peek())
    }
  }
}

context(Resolver)
private fun ResourceImageSource.isVectorDrawable(): Boolean =
  TypedValue().apply {
    request.context.resources.getValue(id, this, /* resolveRefs = */ true)
  }.string.endsWith(".xml")

context(Resolver)
private fun AssetImageSource.canBeSubSampled(): Boolean =
  canBeSubSampled(peek(request.context).source())

context(Resolver)
@SuppressLint("Recycle")
private fun UriImageSource.canBeSubSampled(): Boolean =
  canBeSubSampled(peek(request.context).source())

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
      val bufferedSource = when (this@exists) {
        is ResourceImageSource -> return@withContext true
        is RawImageSource -> peek()
        is AssetImageSource -> peek(request.context).source().buffer()
        is UriImageSource -> peek(request.context).source().buffer()
        is FileImageSource -> FileSystem.SYSTEM.source(path).buffer()
      }
      bufferedSource.read(Buffer(), byteCount = 1) != -1L
    } catch (e: okio.FileNotFoundException) {
      false
    }
  }
}
