package me.saket.telephoto.subsamplingimage.internal

import android.app.ActivityManager
import android.graphics.BitmapRegionDecoder
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.Channel
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder.FactoryParams

/**
 * Maintains a pool of decoders to load multiple bitmap regions in parallel. Without this,
 * a single [BitmapRegionDecoder] can only be used for one region at a time because it
 * synchronizes its APIs internally.
 * */
internal class PooledImageRegionDecoder private constructor(
  override val imageSize: IntSize,
  override val imageOrientation: ExifMetadata.ImageOrientation,
  private val decoders: ResourcePool<ImageRegionDecoder>,
) : ImageRegionDecoder {

  override suspend fun decodeRegion(region: BitmapRegionTile): ImageBitmap {
    return decoders.borrow { decoder ->
      decoder.decodeRegion(region)
    }
  }

  override fun recycle() {
    decoders.resources.forEach { it.recycle() }
  }

  companion object {
    @VisibleForTesting
    internal var overriddenPoolCount: Int? = null

    fun Factory(delegate: ImageRegionDecoder.Factory) = ImageRegionDecoder.Factory { params ->
      val decoders = buildList<ImageRegionDecoder> {
        add(delegate.create(params))
        repeat(calculatePoolCount(params, first().imageSize) - 1) {
          add(delegate.create(params))
        }
      }
      PooledImageRegionDecoder(
        imageSize = decoders.first().imageSize,
        imageOrientation = params.exif.orientation,
        decoders = ResourcePool(decoders),
      )
    }

    private fun calculatePoolCount(params: FactoryParams, imageSize: IntSize): Int {
      overriddenPoolCount?.let {
        return it
      }
      val activityManager = params.context.getSystemService<ActivityManager>()!!
      val memoryInfo = ActivityManager.MemoryInfo().apply(activityManager::getMemoryInfo)
      if (memoryInfo.lowMemory || activityManager.isLowRamDevice) {
        return 1
      }
      // BitmapRegionDecoders are expensive on android. Folks working on android's graphics
      // have suggested not using more than 2 instances to keep memory footprint low.
      // For large images, err on the side of caution and use a single decoder to reduce
      // memory usage, especially for progressive JPEGs.
      return if (imageSize.minDimension < 2_160) {
        Runtime.getRuntime().availableProcessors().coerceAtMost(2)
      } else {
        1
      }
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
