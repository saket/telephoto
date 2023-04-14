package me.saket.telephoto.zoomable.coil

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.DataSource
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.transition.CrossfadeTransition
import com.google.accompanist.drawablepainter.DrawablePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.zoomable.ZoomableImageSource
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import coil.size.Size as CoilSize

internal class CoilImageResolver(
  private val request: ImageRequest,
  private val imageLoader: ImageLoader,
) : RememberWorker() {

  var resolved by mutableStateOf(
    ZoomableImageSource(
      source = null,
      placeholder = null,
      bitmapConfig = request.bitmapConfig,
    )
  )

  override suspend fun work() {
    val result = imageLoader.execute(
      request.newBuilder()
        // Prevent coil from spending any extra effort in downsizing images.
        // For bitmaps, the result will be discarded anyway in favor of their raw files.
        // For animated images, we still want them in full quality so that they can be zoomed.
        .size(CoilSize.ORIGINAL)
        // There's no easy way to be certain whether an image will require sub-sampling in
        // advance so assume it'll be needed and that the image will be read from the disk.
        .diskCachePolicy(
          when (request.diskCachePolicy) {
            CachePolicy.ENABLED -> CachePolicy.ENABLED
            CachePolicy.READ_ONLY -> CachePolicy.ENABLED
            CachePolicy.WRITE_ONLY,
            CachePolicy.DISABLED -> CachePolicy.WRITE_ONLY
          }
        )
        // This will unfortunately replace any existing target, but it is also the only
        // way to read placeholder images set using ImageRequest#placeholderMemoryCacheKey.
        // Placeholder images should be small in size so sub-sampling isn't needed here.
        .target(
          onStart = {
            resolved = resolved.copy(placeholder = it?.asPainter())
          }
        )
        .build()
    )

    val imageSource = result.toSubSamplingImageSource(imageLoader)
    resolved = if (result is SuccessResult && imageSource != null) {
      resolved.copy(
        source = imageSource,
        expectedSize = result.drawable.intrinsicSize,
        crossfadeDuration = result.crossfadeDuration(),
      )
    } else {
      resolved.copy(
        placeholder = result.drawable?.asPainter(),
        source = null,
      )
    }
  }

  @OptIn(ExperimentalCoilApi::class)
  private fun ImageResult.toSubSamplingImageSource(imageLoader: ImageLoader): SubSamplingImageSource? {
    val result = this
    val requestData = result.request.data

    if (result is SuccessResult && result.drawable is BitmapDrawable) {
      // Prefer reading of images directly from files whenever possible because
      // that is significantly faster than reading from their input streams.
      val imageSource = when {
        result.diskCacheKey != null -> {
          val diskCache = imageLoader.diskCache!!
          val cached = diskCache[result.diskCacheKey!!] ?: error("Coil returned a null image from disk cache")
          SubSamplingImageSource.file(cached.data)
        }
        result.dataSource == DataSource.DISK -> when {
          requestData is Uri -> SubSamplingImageSource.contentUri(requestData)
          result.request.context.isResourceId(requestData) -> SubSamplingImageSource.resource(requestData)
          else -> null
        }
        else -> null
      }

      if (imageSource != null) {
        return imageSource
      }
    }

    return null
  }

  private fun ImageResult.crossfadeDuration(): Duration {
    val transitionFactory = request.transitionFactory
    return if (this is SuccessResult && transitionFactory is CrossfadeTransition.Factory) {
      // I'm intentionally not using factory.create() because it optimizes crossfade duration
      // to zero if the image was fetched from memory cache. SubSamplingImage will only read
      // bitmaps from the disk so there will always be some delay in showing the image.
      transitionFactory.durationMillis.milliseconds
    } else {
      Duration.ZERO
    }
  }
}

private fun Drawable.asPainter(): Painter {
  return DrawablePainter(mutate())
}

private val Drawable.intrinsicSize
  get() = Size(width = intrinsicWidth.toFloat(), height = intrinsicHeight.toFloat())

@OptIn(ExperimentalContracts::class)
private fun Context.isResourceId(data: Any): Boolean {
  contract {
    returns(true) implies (data is Int)
  }

  if (data is Int) {
    runCatching {
      resources.getResourceEntryName(data)
      return true
    }
  }
  return false
}

internal abstract class RememberWorker : RememberObserver {
  private lateinit var job: Job

  abstract suspend fun work()

  override fun onRemembered() {
    check(!::job.isInitialized) // Shouldn't be remembered in multiple locations.
    job = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch { work() }
  }

  override fun onAbandoned() = job.cancel()

  override fun onForgotten() = job.cancel()
}
