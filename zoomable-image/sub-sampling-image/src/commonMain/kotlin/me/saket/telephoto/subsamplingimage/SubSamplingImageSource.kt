package me.saket.telephoto.subsamplingimage

import android.content.Context
import android.graphics.BitmapRegionDecoder
import androidx.compose.ui.graphics.ImageBitmap
import okio.BufferedSource
import okio.Closeable

/**
 * Image to display with [SubSamplingImage]. See:
 *
 * * [SubSamplingImageSource.file]
 * * [SubSamplingImageSource.asset]
 * * [SubSamplingImageSource.resource]
 * * [SubSamplingImageSource.contentUri]
 * * [SubSamplingImageSource.rawSource]
 */
interface SubSamplingImageSource : Closeable {
  /**
   * A preview that can be displayed immediately while the bitmap tiles
   * are loaded, which can be slightly slow depending on the file size.
   */
  val preview: ImageBitmap?

  /** Peeks into the source without consuming its bytes. */
  fun peek(context: Context): BufferedSource

  suspend fun decoder(context: Context): BitmapRegionDecoder

  /** Called when the image is no longer visible. */
  override fun close() = Unit

  companion object; // For extensions.
}
