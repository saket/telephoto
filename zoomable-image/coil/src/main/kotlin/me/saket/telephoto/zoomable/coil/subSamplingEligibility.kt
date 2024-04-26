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
import okio.FileSystem
import okio.Source
import okio.buffer
import okio.source

context(Resolver)
internal suspend fun SubSamplingImageSource.canBeSubSampled(): Boolean {
  val preventSubSampling = when (this) {
    is ResourceImageSource -> isVectorDrawable()
    is AssetImageSource -> canBeSubSampled()
    is UriImageSource -> canBeSubSampled()
    is FileImageSource -> canBeSubSampled(FileSystem.SYSTEM.source(path))
    is RawImageSource -> canBeSubSampled(source.invoke())
  }
  return !preventSubSampling
}

context(Resolver)
private fun ResourceImageSource.isVectorDrawable(): Boolean =
  TypedValue().apply {
    request.context.resources.getValue(id, this, /* resolveRefs = */ true)
  }.string.endsWith(".xml")

context(Resolver)
private suspend fun AssetImageSource.canBeSubSampled(): Boolean =
  canBeSubSampled(peek(request.context).source())

context(Resolver)
@SuppressLint("Recycle")
private suspend fun UriImageSource.canBeSubSampled(): Boolean =
  canBeSubSampled(peek(request.context).source())

private suspend fun canBeSubSampled(source: Source): Boolean {
  return withContext(Dispatchers.IO) {
    source.buffer().use {
      DecodeUtils.isSvg(it) || DecodeUtils.isGif(it)
    }
  }
}
