package me.saket.telephoto.subsamplingimage.internal

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toAndroidRect
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.AssetImageSource
import me.saket.telephoto.subsamplingimage.ImageSource
import java.io.InputStream
import kotlin.math.max

/**
 * Pools multiple [BitmapRegionDecoder]s to concurrently load multiple bitmap regions at the same time.
 * A single decoder can only be used for one region at a time because it synchronizes its APIs internally.
 * */
// todo: doc
internal class SkiaImageRegionDecoders private constructor(
  override val imageSize: Size,
  private val imageSource: ImageSource,
  private val decoders: ResourcePool<BitmapRegionDecoder>,
  private val dispatcher: CoroutineDispatcher
) : ImageRegionDecoder {

  override suspend fun decodeRegion(region: BitmapRegionTile): ImageBitmap {
    val options = BitmapFactory.Options().apply {
      inSampleSize = region.sampleSize.size
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = decoders.borrow { decoder ->
      withContext(dispatcher) {
        decoder.decodeRegion(region.bounds.toAndroidRect(), options)
      }
    }

    checkNotNull(bitmap) {
      "BitmapRegionDecoder returned a null bitmap. Image format may not be supported: $imageSource."
    }
    return bitmap.asImageBitmap()
  }

  companion object {
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun create(context: Context, imageSource: ImageSource): SkiaImageRegionDecoders {
      val decoderCount = max(Runtime.getRuntime().availableProcessors(), 2) // Same number used by Dispatchers.Default.
      val dispatcher = Dispatchers.Default.limitedParallelism(decoderCount)

      val decoders = withContext(dispatcher) {
        (0 until decoderCount).map {
          imageSource.inputStream(context).use { stream ->
            BitmapRegionDecoder.newInstance(stream, /* ignored */ false)!!
          }
        }
      }
      return SkiaImageRegionDecoders(
        imageSource = imageSource,
        imageSize = decoders.first().size(),
        decoders = ResourcePool(decoders),
        dispatcher = dispatcher,
      )
    }
  }
}

private fun ImageSource.inputStream(context: Context): InputStream {
  return when (this) {
    is AssetImageSource -> {
      context.assets.open(assetName, AssetManager.ACCESS_RANDOM)
    }
  }
}

private fun BitmapRegionDecoder.size(): Size {
  return Size(
    width = width.toFloat(),
    height = height.toFloat()
  )
}

private class ResourcePool<T>(resources: List<T>) {
  private val channel = Channel<T>(Channel.UNLIMITED).apply {
    resources.forEach(::trySend)
  }

  suspend fun <R> borrow(handler: suspend (T) -> R): R {
    val borrowed = channel.receive()
    return try {
      handler(borrowed)
    } finally {
      channel.send(borrowed)
    }
  }
}
