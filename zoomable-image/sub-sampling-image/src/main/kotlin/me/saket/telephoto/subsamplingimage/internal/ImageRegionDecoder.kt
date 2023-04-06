package me.saket.telephoto.subsamplingimage.internal

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import me.saket.telephoto.subsamplingimage.ImageSource

// todo: doc.
internal interface ImageRegionDecoder {
  val imageSize: IntSize
  suspend fun decodeRegion(region: BitmapRegionTile): ImageBitmap

  fun interface Factory {
    suspend fun create(
      context: Context,
      imageSource: ImageSource,
      bitmapConfig: Bitmap.Config
    ): ImageRegionDecoder
  }
}

// Used for overriding the decoder in screenshot tests.
internal val LocalImageRegionDecoderFactory =
  staticCompositionLocalOf<ImageRegionDecoder.Factory> { SkiaImageRegionDecoders.Factory }
