package me.saket.telephoto.subsamplingimage.internal

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import me.saket.telephoto.subsamplingimage.ImageSource

// todo: doc.
internal interface ImageRegionDecoder {
  val imageSize: Size
  suspend fun decodeRegion(region: BitmapRegionTile): ImageBitmap

  interface Factory {
    suspend fun create(context: Context, imageSource: ImageSource): ImageRegionDecoder
  }
}

// Used for overriding the decoder in screenshot tests.
internal val LocalImageRegionDecoderFactory =
  staticCompositionLocalOf<ImageRegionDecoder.Factory> { SkiaImageRegionDecoders.Factory }
