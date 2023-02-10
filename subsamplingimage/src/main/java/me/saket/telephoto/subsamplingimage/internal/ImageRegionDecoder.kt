package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap

// todo: doc.
internal interface ImageRegionDecoder {
  val imageSize: Size

  suspend fun decodeRegion(region: BitmapRegionBounds, sampleSize: BitmapSampleSize): ImageBitmap
}
