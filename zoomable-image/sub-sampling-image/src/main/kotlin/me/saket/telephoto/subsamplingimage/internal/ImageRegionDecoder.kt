package me.saket.telephoto.subsamplingimage.internal

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource

/**
 * [ImageBitmap] decoder, responsible for loading regions of an image for [SubSamplingImage]'s tiles.
 *
 * Also see: [AndroidImageRegionDecoder] and [PooledImageRegionDecoder].
 */
internal interface ImageRegionDecoder {
  val imageSize: IntSize

  suspend fun decodeRegion(region: BitmapRegionTile): ImageBitmap

  fun recycle()

  fun interface Factory {
    suspend fun create(
      context: Context,
      imageSource: SubSamplingImageSource,
      imageOptions: ImageBitmapOptions
    ): ImageRegionDecoder
  }
}

// Used for overriding the decoder in screenshot tests.
internal val LocalImageRegionDecoderFactory = staticCompositionLocalOf { AndroidImageRegionDecoder.Factory }
