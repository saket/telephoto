package me.saket.telephoto.subsamplingimage.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.unit.IntSize
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource

internal class AndroidImageRegionDecoder private constructor(
  private val imageSource: SubSamplingImageSource,
  private val bitmapConfig: Bitmap.Config,
  private val decoder: BitmapRegionDecoder,
) : ImageRegionDecoder {

  override val imageSize: IntSize = decoder.size()

  override suspend fun decodeRegion(region: BitmapRegionTile): ImageBitmap {
    val options = BitmapFactory.Options().apply {
      inSampleSize = region.sampleSize.size
      inPreferredConfig = bitmapConfig
    }
    val bitmap = decoder.decodeRegion(region.bounds.toAndroidRect(), options)
    checkNotNull(bitmap) {
      "BitmapRegionDecoder returned a null bitmap. Image format may not be supported: $imageSource."
    }
    return bitmap.asImageBitmap()
  }

  companion object {
    val Factory = ImageRegionDecoder.Factory { context, imageSource, bitmapConfig ->
      AndroidImageRegionDecoder(
        imageSource = imageSource,
        bitmapConfig = bitmapConfig,
        decoder = imageSource.decoder(context),
      )
    }
  }
}

private fun BitmapRegionDecoder.size(): IntSize {
  return IntSize(
    width = width,
    height = height,
  )
}
