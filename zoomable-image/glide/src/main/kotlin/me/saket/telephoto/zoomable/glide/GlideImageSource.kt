@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

package me.saket.telephoto.zoomable.glide

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.DrawableCrossFadeTransition
import com.bumptech.glide.request.transition.Transition
import com.google.accompanist.drawablepainter.DrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableImageSource.ResolveResult
import me.saket.telephoto.zoomable.internal.RememberWorker
import okio.Path
import okio.Path.Companion.toOkioPath
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import me.saket.telephoto.zoomable.glide.Size as GlideSize
import me.saket.telephoto.zoomable.internal.copy

internal class GlideImageSource(
  private val requestManager: RequestManager,
  private val request: RequestBuilder<Drawable>,
  private val isVectorDrawable: Boolean,
) : ZoomableImageSource {

  @Composable
  override fun resolve(canvasSize: Flow<Size>): ResolveResult {
    val resolver = remember(this) {
      GlideImageResolver(
        request = request,
        requestManager = requestManager,
        size = { canvasSize.first().toGlideSize() },
        isVectorDrawable = isVectorDrawable,
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
  private val isVectorDrawable: Boolean,
) : RememberWorker() {

  var resolved: ResolveResult by mutableStateOf(
    ResolveResult(delegate = null)
  )

  override suspend fun work() {
    request
      .clone()
      // There's no easy way to be certain whether an image will require sub-sampling in
      // advance so assume it'll be needed and force Glide to write this image to disk.
      .diskCacheStrategy(
        object : ForwardingDiskCacheStrategy(request.diskCacheStrategy) {
          override fun isDataCacheable(dataSource: DataSource): Boolean = when {
            isVectorDrawable -> super.isDataCacheable(dataSource) // https://github.com/bumptech/glide/issues/5213
            else -> DATA.isDataCacheable(dataSource)
          }
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
              resolved = resolved.copy(
                placeholder = instant.placeholder.asPainter()
              )
            }
          }
          is Resource -> {
            resolved = if (instant.status == Status.SUCCEEDED) {
              val subSamplingSource = (instant.resource as? BitmapDrawable)?.let { drawable ->
                // Prefer reading of images directly from files whenever possible because
                // that is significantly faster than reading from their input streams.
                // The image should be cached so downloading it again should be super fast.
                request.downloadAsFile()?.let { file ->
                  SubSamplingImageSource.file(file, preview = drawable.bitmap.asImageBitmap())
                }
              }
              resolved.copy(
                crossfadeDuration = instant.transition.crossfadeDuration(),
                delegate = if (subSamplingSource != null) {
                  ZoomableImageSource.SubSamplingDelegate(
                    source = subSamplingSource,
                    imageOptions = ImageBitmapOptions(from = instant.resource.bitmap)
                  )
                } else {
                  ZoomableImageSource.PainterDelegate(
                    painter = instant.resource.asPainter()
                  )
                }
              )
            } else {
              // This must be a thumbnail. Treat this as a placeholder.
              ResolveResult(
                delegate = null,
                placeholder = instant.resource.asPainter(),
              )
            }
          }
        }
      }
  }

  private suspend fun RequestBuilder<Drawable>.downloadAsFile(): Path? {
    return withContext(Dispatchers.IO) {
      try {
        @Suppress("DEPRECATION")
        clone().downloadOnly(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).get().toOkioPath()
      } catch (e: Throwable) {
        e.printStackTrace()
        null
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
