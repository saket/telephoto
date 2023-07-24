package me.saket.telephoto.subsamplingimage.internal

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.AssetImageSource
import me.saket.telephoto.subsamplingimage.FileImageSource
import me.saket.telephoto.subsamplingimage.RawImageSource
import me.saket.telephoto.subsamplingimage.ResourceImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.UriImageSource
import okio.FileSystem
import okio.buffer
import java.io.InputStream

/** Properties read from an image's EXIF header. */
@JvmInline
internal value class ExifMetadata(
  val rotationDegrees: Int,
) {

  init {
    check(rotationDegrees.rem(90) == 0) {
      "Unsupported image orientation at $rotationDegrees°"
    }
  }

  companion object {
    suspend fun read(context: Context, source: SubSamplingImageSource): ExifMetadata {
      return withContext(Dispatchers.Default) {
        val inputStream = when (source) {
          is FileImageSource -> FileSystem.SYSTEM.source(source.path).buffer().inputStream()
          is RawImageSource -> source.peek().inputStream()
          is AssetImageSource -> source.peek(context)
          is ResourceImageSource -> source.peek(context)
          is UriImageSource -> source.peek(context)
        }
        val exif = ExifInterface(
          ExifInterfaceCompatibleInputStream(inputStream)
        )
        ExifMetadata(
          rotationDegrees = exif.rotationDegrees,
        )
      }
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
