package me.saket.telephoto.subsamplingimage.internal

import android.app.ActivityManager
import android.content.Context
import android.graphics.BitmapRegionDecoder
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.ImageRegionDecoder

/**
 * Maintains a pool of decoders to load multiple bitmap regions in parallel. Without this,
 * a single [BitmapRegionDecoder] can only be used for one region at a time because it
 * synchronizes its APIs internally.
 * */
internal class PooledImageRegionDecoder private constructor(
  override val imageSize: IntSize,
  private val decoders: ResourcePool<ImageRegionDecoder>,
  private val dispatcher: CoroutineDispatcher,
) : ImageRegionDecoder {

  override suspend fun decodeRegion(region: BitmapRegionTile): ImageBitmap {
    return decoders.borrow { decoder ->
      withContext(dispatcher) {
        decoder.decodeRegion(region)
      }
    }
  }

  override fun recycle() {
    decoders.resources.forEach { it.recycle() }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  companion object {
    @VisibleForTesting
    internal var overriddenMinPoolCount: Int? = null

    fun Factory(
      delegate: ImageRegionDecoder.Factory,
    ) = ImageRegionDecoder.Factory { context, imageSource, imageOptions ->
      val decoderCount = calculatePoolCount(context)
      val dispatcher = Dispatchers.Default.limitedParallelism(decoderCount)

      val decoders = withContext(dispatcher) {
        List(decoderCount) {
          delegate.create(
            context = context,
            imageSource = imageSource,
            imageOptions = imageOptions,
          )
        }
      }
      PooledImageRegionDecoder(
        imageSize = decoders.first().imageSize,
        decoders = ResourcePool(decoders),
        dispatcher = dispatcher,
      )
    }

    private fun calculatePoolCount(context: Context): Int {
      val activityManager = context.getSystemService<ActivityManager>()!!
      if (activityManager.isLowRamDevice) {
        return 1
      }
      val memoryInfo = ActivityManager.MemoryInfo().apply(activityManager::getMemoryInfo)
      if (memoryInfo.lowMemory) {
        return 1
      }
      return Runtime.getRuntime().availableProcessors()
        .coerceAtLeast(2) // Same number used by Dispatchers.Default.
        .coerceAtLeast(overriddenMinPoolCount ?: 1)
    }
  }
}

private class ResourcePool<T>(val resources: List<T>) {
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
