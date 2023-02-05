package me.saket.telephoto.subsamplingimage.internal

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.AssetImageSource
import me.saket.telephoto.subsamplingimage.ImageSource
import java.io.IOException

internal class SkiaImageRegionDecoder(
  private val decoder: BitmapRegionDecoder,
  private val imageSource: ImageSource,
) {
  val imageSize = IntSize(
    width = decoder.width,
    height = decoder.height
  )

  fun decodeRegion(region: Rect, sampleSize: BitmapSampleSize): Bitmap {
    val options = BitmapFactory.Options().apply {
      inSampleSize = sampleSize.size
      inPreferredConfig = Bitmap.Config.RGB_565
    }
    val bitmap = decoder.decodeRegion(region.toAndroidRect(), options)
    return checkNotNull(bitmap) {
      "Skia image decoder returned a null bitmap. Image format may not be supported: $imageSource."
    }
  }

  companion object {
    @Throws(IOException::class)
    suspend fun create(context: Context, imageSource: ImageSource): SkiaImageRegionDecoder {
      val decoder = withContext(Dispatchers.IO) {
        when (imageSource) {
          is AssetImageSource -> {
            val inputStream = context.assets.open(imageSource.assetName, AssetManager.ACCESS_RANDOM)
            BitmapRegionDecoder.newInstance(inputStream, false)!!
          }
        }
      }
      return SkiaImageRegionDecoder(decoder, imageSource)
    }
  }
}
