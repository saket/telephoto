package me.saket.telephoto.zoomable.glide

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.DrawableCrossFadeTransition
import com.bumptech.glide.request.transition.Transition
import com.google.accompanist.drawablepainter.DrawablePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.zoomable.ZoomableImageSource
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import me.saket.telephoto.zoomable.glide.Size as GlideSize

internal class GlideImageResolver(
  private val request: RequestBuilder<Drawable>,
  private val requestManager: RequestManager,
  private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : RememberWorker() {

  var resolved: ZoomableImageSource by mutableStateOf(
    ZoomableImageSource.Generic(image = null)
  )

  override suspend fun work() {
    request
      // There's no easy way to be certain whether an image will require sub-sampling in
      // advance so assume it'll be needed and force Glide to write this image to disk.
      .diskCacheStrategy(
        object : ForwardingDiskCacheStrategy(request.diskCacheStrategy) {
          override fun isDataCacheable(dataSource: DataSource) = DATA.isDataCacheable(dataSource)
        }
      )
      .flow(
        waitForSize = { GlideSize(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL) },
        requestManager = requestManager,
      )
      .collect { instant ->
        when (instant) {
          is Placeholder -> {
            if (instant.placeholder != null) {
              // Placeholder images should be small in size so sub-sampling isn't needed here.
              resolved = ZoomableImageSource.Generic(
                image = null,
                placeholder = instant.placeholder.asPainter(),
              )
            }
          }
          is Resource -> {
            resolved = if (instant.status == Status.SUCCEEDED) {
              val subSamplingSource = (instant.resource as? BitmapDrawable)?.let {
                // Prefer reading of images directly from files whenever possible because
                // that is significantly faster than reading from their input streams.
                request.downloadAsFile()?.let {
                  SubSamplingImageSource.file(it.toOkioPath())
                }
              }
              if (subSamplingSource != null) {
                ZoomableImageSource.RequiresSubSampling(
                  source = subSamplingSource,
                  placeholder = resolved.placeholder,
                  expectedSize = instant.resource.intrinsicSize,
                  crossfadeDuration = instant.transition.crossfadeDuration(),
                )
              } else {
                ZoomableImageSource.Generic(
                  placeholder = resolved.placeholder,
                  image = instant.resource.asPainter(),
                  crossfadeDuration = instant.transition.crossfadeDuration(),
                )
              }
            } else {
              // This must be a thumbnail. Treat this as a placeholder.
              ZoomableImageSource.Generic(
                image = null,
                placeholder = instant.resource.asPainter(),
              )
            }
          }
        }
      }
  }

  private suspend fun RequestBuilder<Drawable>.downloadAsFile(): File? {
    return withContext(ioDispatcher) {
      suspendCancellableCoroutine { continuation ->
        val target: Target<File> = downloadOnly(object : CustomTarget<File>() {
          override fun onLoadCleared(placeholder: Drawable?) {
            continuation.cancel()
          }

          override fun onResourceReady(resource: File, transition: Transition<in File>?) {
            continuation.resume(resource)
          }

          override fun onLoadFailed(errorDrawable: Drawable?) {
            continuation.resume(null)
          }
        })
        continuation.invokeOnCancellation {
          requestManager.clear(target)
        }
      }
    }
  }

  private fun Transition<in Drawable>?.crossfadeDuration(): Duration {
    return if (this is DrawableCrossFadeTransition) {
      // TODO: remove this when https://github.com/bumptech/glide/issues/5123 is resolved.
      runCatching {
        val field = DrawableCrossFadeTransition::class.java.getDeclaredField("duration")
        field.isAccessible = true
        (field.get(this) as Int).milliseconds
      }.getOrElse { Duration.ZERO }
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
