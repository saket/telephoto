package me.saket.telephoto.subsamplingimage.internal

import android.content.Context
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions

/**
 * An image decoder, responsible for loading partial regions for
 * [SubSamplingImage][me.saket.telephoto.subsamplingimage.SubSamplingImage]'s tiles.
 *
 * Also see: [AndroidImageRegionDecoder] and [PooledAndroidImageRegionDecoder].
 */
interface ImageRegionDecoder {
  /** Size of the full image, without any scaling applied. */
  val imageSize: IntSize

  /**
   * Decodes a specific region of the image.
   *
   * When `sampleSize > 1`, the partial image is downsized by sub-sampling the pixels.
   * The sample size is the number of pixels in either dimension that correspond to a
   * single pixel in the decoded image. For example, a value of `sampleSize == 4` returns
   * an image that is 1/4 the width/height of the original, and 1/16 the number of pixels.
   * Values smaller than 1 are treated the same as 1.
   *
   * This is designed to be called concurrently for all visible regions of the image.
   * Implementations must handle synchronization if needed.
   *
   * Implementations should throw an [IOException][java.io.IOException] if the region can't be
   * decoded. The failure is reported through
   * [SubSamplingImageErrorReporter][me.saket.telephoto.subsamplingimage.SubSamplingImageErrorReporter]
   * instead of crashing the app.
   */
  suspend fun decodeRegion(region: IntRect, sampleSize: Int): DecodeResult

  /** Called when the image is no longer visible. */
  fun close() = Unit

  fun interface Factory {
    suspend fun create(params: FactoryParams): ImageRegionDecoder
  }

  @Poko
  class DecodeResult(
    val painter: Painter,
    val hasUltraHdrContent: Boolean,
  )

  interface FactoryParams {
    val imageOptions: ImageBitmapOptions
  }
}

internal data class AndroidImageDecoderFactoryParams(
  val context: Context,
  override val imageOptions: ImageBitmapOptions,
) : ImageRegionDecoder.FactoryParams
