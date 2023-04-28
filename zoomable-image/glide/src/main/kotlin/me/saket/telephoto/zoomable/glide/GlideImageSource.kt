package me.saket.telephoto.zoomable.glide

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.DrawableCrossFadeTransition
import com.bumptech.glide.request.transition.Transition
import com.google.accompanist.drawablepainter.DrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.internal.RememberWorker
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import me.saket.telephoto.zoomable.glide.Size as GlideSize

internal class GlideImageSource(
  private val model: Any?
) : ZoomableImageSource {

  @Composable
  @Suppress("UNCHECKED_CAST")
  override fun resolve(canvasSize: Flow<Size>): ZoomableImageSource.ResolveResult {
    val context = LocalContext.current
    val resolver = remember(this) {
      val requestManager = Glide.with(context)
      GlideImageResolver(
        request = model as? RequestBuilder<Drawable> ?: requestManager.load(model),
        requestManager = requestManager,
        size = { canvasSize.first().toGlideSize() },
        ioDispatcher = Dispatchers.IO,
      )
    }
    return resolver.resolved
  }

  private fun Size.toGlideSize() = GlideSize(
    width = if (width.isFinite()) width.roundToInt() else Target.SIZE_ORIGINAL,
    height = if (height.isFinite()) height.roundToInt() else Target.SIZE_ORIGINAL
  )
}

private class GlideImageResolver(
  private val request: RequestBuilder<Drawable>,
  private val requestManager: RequestManager,
  private val size: suspend () -> GlideSize,
  private val ioDispatcher: CoroutineContext,
) : RememberWorker() {

  var resolved: ZoomableImageSource.ResolveResult by mutableStateOf(
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
        waitForSize = size,
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
                  imageOptions = ImageBitmapOptions.Default // Glide does not expose the config so use a default value.
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
