package me.saket.telephoto.subsamplingimage.internal

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource

// todo: doc.
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
internal val LocalImageRegionDecoderFactory = staticCompositionLocalOf {
  PooledImageRegionDecoder.Factory(
    AndroidImageRegionDecoder.Factory
  )
}
