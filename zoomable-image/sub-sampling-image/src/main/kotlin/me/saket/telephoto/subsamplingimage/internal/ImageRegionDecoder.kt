package me.saket.telephoto.subsamplingimage.internal

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource

/**
 * [ImageBitmap] decoder, responsible for loading regions of an image for [SubSamplingImage]'s tiles.
 *
 * Also see: [AndroidImageRegionDecoder] and [PooledImageRegionDecoder].
 */
internal interface ImageRegionDecoder {
  val imageSize: IntSize
  val imageOrientation: ExifMetadata.ImageOrientation

  suspend fun decodeRegion(region: BitmapRegionTile): ImageBitmap

  fun recycle()

  fun interface Factory {
    suspend fun create(params: FactoryParams): ImageRegionDecoder
  }

  class FactoryParams(
    val context: Context,
    val imageSource: SubSamplingImageSource,
    val imageOptions: ImageBitmapOptions,
    val exif: ExifMetadata,
  )
}

// Used for overriding the decoder in screenshot tests.
internal val LocalImageRegionDecoderFactory = staticCompositionLocalOf { AndroidImageRegionDecoder.Factory }
