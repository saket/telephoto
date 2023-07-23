package me.saket.telephoto.subsamplingimage.internal

import androidx.exifinterface.media.ExifInterface
import okio.BufferedSource
import java.io.InputStream

/** Properties read from an image's EXIF header. */
internal data class ExifMetadata(
  val isFlipped: Boolean,
  val rotationDegrees: Int,
) {

  companion object {
    fun read(source: BufferedSource): ExifMetadata {
      val exif = ExifInterface(
        ExifInterfaceCompatibleInputStream(source.peek().inputStream())
      )
      return ExifMetadata(
        isFlipped = exif.isFlipped,
        rotationDegrees = exif.rotationDegrees,
      )
    }
  }
}

/**
 * Copied from Coil.
 * https://github.com/coil-kt/coil/blob/65be959aabdb8165b483106a35040a2ebca1a196/coil-base/src/main/java/coil/decode/ExifUtils.kt#L106
 */
private class ExifInterfaceCompatibleInputStream(private val delegate: InputStream) : InputStream() {
  /**
   * Ensure that this value is always larger than the size of the image
   * so ExifInterface won't stop reading the stream prematurely.
   */
  private var availableBytes = 1024 * 1024 * 1024 // 1GB

  override fun available() = availableBytes

  override fun read() = interceptBytesRead(delegate.read())
  override fun read(b: ByteArray) = interceptBytesRead(delegate.read(b))
  override fun read(b: ByteArray, off: Int, len: Int) = interceptBytesRead(delegate.read(b, off, len))

  private fun interceptBytesRead(bytesRead: Int): Int {
    if (bytesRead == -1) availableBytes = 0
    return bytesRead
  }

  override fun close() = delegate.close()
  override fun skip(n: Long) = delegate.skip(n)
}
