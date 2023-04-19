package me.saket.telephoto.subsamplingimage.internal

import android.graphics.BitmapRegionDecoder
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * Pools multiple [BitmapRegionDecoder]s to concurrently load multiple bitmap regions at the same time.
 * A single decoder can only be used for one region at a time because it synchronizes its APIs internally.
 * */
// todo: doc
internal class PooledImageRegionDecoder private constructor(
  override val imageSize: IntSize,
  private val decoders: ResourcePool<ImageRegionDecoder>,
  private val dispatcher: CoroutineDispatcher,
) : ImageRegionDecoder {

  @OptIn(ExperimentalTime::class)
  override suspend fun decodeRegion(region: BitmapRegionTile): ImageBitmap {
    return decoders.borrow { decoder ->
      withContext(dispatcher) {
        println("decodeRegion(region=${region.bounds})")
        measureTimedValue {
          decoder.decodeRegion(region)
        }.let {
          println("Decoded bitmap in ${it.duration}")
          it.value
        }
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  companion object {
    fun Factory(
      delegate: ImageRegionDecoder.Factory,
    ) = ImageRegionDecoder.Factory { context, imageSource, bitmapConfig ->
      val decoderCount = max(Runtime.getRuntime().availableProcessors(), 2) // Same number used by Dispatchers.Default.
      val dispatcher = Dispatchers.Default.limitedParallelism(decoderCount)

      println("decoderCount = $decoderCount")
      val decoders = withContext(dispatcher) {
        List(decoderCount) {
          delegate.create(
            context = context,
            imageSource = imageSource,
            bitmapConfig = bitmapConfig,
          )
        }
      }
      PooledImageRegionDecoder(
        imageSize = decoders.first().imageSize,
        decoders = ResourcePool(decoders),
        dispatcher = dispatcher,
      )
    }
  }
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
