package me.saket.telephoto.zoomable.coil

import coil.ImageLoader
import coil.decode.DecodeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.Source
import okio.buffer

private val SVG_TAG: ByteString = "<svg".encodeUtf8()
private val LEFT_ANGLE_BRACKET: ByteString = "<".encodeUtf8()

internal fun ImageLoader.hasSvgDecoder(): Boolean {
  return components.decoderFactories.any {
    it::class.qualifiedName?.contains("svg", ignoreCase = true) == true
  }
}

class SvgChecker(private val source: () -> Source?) {
  suspend fun isSvg(): Boolean {
    return withContext(Dispatchers.IO) {
      source()?.buffer()?.use(DecodeUtils::isSvg) == true
    }
  }
}

/**
 * Copied from coil-svg.
 * TODO: propose coil to move this function to the core coil module.
 *
 * Return 'true' if the [source] contains an SVG image. The [source] is not consumed.
 *
 * NOTE: There's no guaranteed method to determine if a byte stream is an SVG without attempting
 * to decode it. This method uses heuristics.
 */
@Suppress("UnusedReceiverParameter")
internal fun DecodeUtils.isSvg(source: BufferedSource): Boolean {
  return source.rangeEquals(0, LEFT_ANGLE_BRACKET) &&
    source.indexOf(SVG_TAG, 0, 1024) != -1L
}

internal fun BufferedSource.indexOf(bytes: ByteString, fromIndex: Long, toIndex: Long): Long {
  require(bytes.size > 0) { "bytes is empty" }

  val firstByte = bytes[0]
  val lastIndex = toIndex - bytes.size
  var currentIndex = fromIndex
  while (currentIndex < lastIndex) {
    currentIndex = indexOf(firstByte, currentIndex, lastIndex)
    if (currentIndex == -1L || rangeEquals(currentIndex, bytes)) {
      return currentIndex
    }
    currentIndex++
  }
  return -1
}
