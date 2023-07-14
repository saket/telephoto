package me.saket.telephoto.zoomable.coil

import coil.decode.DecodeUtils
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

private val SVG_TAG: ByteString = "<svg".encodeUtf8()
private val LEFT_ANGLE_BRACKET: ByteString = "<".encodeUtf8()

/**
 * Copied from coil-svg.
 * TODO: remove this if https://github.com/coil-kt/coil/issues/1811 is accepted.
 */
@Suppress("UnusedReceiverParameter")
internal fun DecodeUtils.isSvg(source: BufferedSource): Boolean {
  return source.rangeEquals(0, LEFT_ANGLE_BRACKET) &&
    source.indexOf(SVG_TAG, 0, 1024) != -1L
}

/** Copied from coil-svg. */
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
