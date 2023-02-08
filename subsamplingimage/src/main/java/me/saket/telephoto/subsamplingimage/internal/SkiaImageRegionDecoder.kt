package me.saket.telephoto.subsamplingimage.internal

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAndroidRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.AssetImageSource
import me.saket.telephoto.subsamplingimage.ImageSource
import java.io.IOException
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

internal class SkiaImageRegionDecoder(
  private val decoder: BitmapRegionDecoder,
  private val imageSource: ImageSource,
) {
  val imageSize = Size(
    width = decoder.width.toFloat(),
    height = decoder.height.toFloat()
  )

  @OptIn(ExperimentalTime::class)
  suspend fun decodeRegion(region: BitmapRegionBounds, sampleSize: BitmapSampleSize): Bitmap {
    val options = BitmapFactory.Options().apply {
      inSampleSize = sampleSize.size
      inPreferredConfig = Bitmap.Config.RGB_565
    }
    val timed = measureTimedValue {
      withContext(Dispatchers.IO) {
        decoder.decodeRegion(region.bounds.toAndroidRect(), options)
      }
    }
    println("Decoded bitmap in ${timed.duration} for $region")
    val bitmap = timed.value
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
