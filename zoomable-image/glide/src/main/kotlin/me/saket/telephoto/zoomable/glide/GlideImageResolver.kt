package me.saket.telephoto.zoomable.glide

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.integration.ktx.ExperimentGlideFlows
import com.bumptech.glide.integration.ktx.Placeholder
import com.bumptech.glide.integration.ktx.Resource
import com.bumptech.glide.integration.ktx.Status
import com.bumptech.glide.integration.ktx.flow
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.google.accompanist.drawablepainter.DrawablePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.zoomable.ZoomableImageSource
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class GlideImageResolver(
  private val request: RequestBuilder<Drawable>,
  private val requestManager: RequestManager,
  private val ioDispatcher: CoroutineContext = Dispatchers.IO,
  private val context: Context,
) : RememberWorker() {

  var resolved: ZoomableImageSource by mutableStateOf(
    ZoomableImageSource.Generic(EmptyPainter)
  )

  @OptIn(ExperimentGlideFlows::class)
  override suspend fun work() {
    request
      // There's no easy way to be certain whether an image will require sub-sampling in
      // advance so assume it'll be needed and force Glide to write this image to disk.
      .diskCacheStrategy(
        object : ForwardingDiskCacheStrategy(request.diskCacheStrategy) {
          override fun isDataCacheable(dataSource: DataSource) = DATA.isDataCacheable(dataSource)
        }
      )
      .addListener(object : RequestListener<Drawable> {
        override fun onLoadFailed(
          e: GlideException?,
          model: Any,
          target: Target<Drawable>,
          isFirstResource: Boolean
        ): Boolean {
          return false
        }

        override fun onResourceReady(
          resource: Drawable,
          model: Any,
          target: Target<Drawable>,
          dataSource: DataSource,
          isFirstResource: Boolean
        ): Boolean {
          println("Loading of $model done. Is resource id? ${context.isResourceId(model)}")
          return false
        }
      })
      .flow(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
      .collect { instant ->
        when (instant) {
          is Placeholder -> {
            // Placeholder images should be small in size so sub-sampling isn't needed here.
            resolved = ZoomableImageSource.Generic(
              image = EmptyPainter,
              placeholder = instant.placeholder?.asPainter(),
            )
          }
          is Resource -> {
            val imageSource = instant.toSubSamplingImageSource(request)
            resolved = if (instant.status == Status.SUCCEEDED && imageSource != null) {
              ZoomableImageSource.RequiresSubSampling(
                source = imageSource,
                placeholder = resolved.placeholder,
                expectedSize = instant.resource.intrinsicSize,
                crossfadeDuration = Duration.ZERO,  // todo: read transition duration somehow.
              )
            } else {
              ZoomableImageSource.Generic(
                placeholder = resolved.placeholder,
                image = instant.resource?.asPainter() ?: EmptyPainter,
                crossfadeDuration = Duration.ZERO,  // todo: read transition duration somehow.
              )
            }
          }
        }
      }
  }

  @OptIn(ExperimentGlideFlows::class)
  private suspend fun Resource<Drawable>.toSubSamplingImageSource(request: RequestBuilder<Drawable>): SubSamplingImageSource? {
    val resource = this
    val requestData = request

    if (resource.status == Status.SUCCEEDED && resource.resource is BitmapDrawable) {
      // Prefer reading of images directly from files whenever possible because
      // that is significantly faster than reading from their input streams.
      val file: File = withContext(ioDispatcher) {
        suspendCancellableCoroutine { continuation ->
          val target: Target<File> = object : CustomTarget<File>() {
            override fun onLoadCleared(placeholder: Drawable?) {
              continuation.cancel()
            }

            override fun onResourceReady(resource: File, transition: Transition<in File>?) {
              continuation.resume(resource)
            }
          }
          request.downloadOnly(target)
          continuation.invokeOnCancellation {
            requestManager.clear(target)
          }
        }
      }
      return SubSamplingImageSource.file(file.toOkioPath())

//      val imageSource = when {
//        resource.diskCacheKey != null -> {
//          val diskCache = imageLoader.diskCache!!
//          val cached = diskCache[resource.diskCacheKey!!] ?: error("Coil returned a null image from disk cache")
//          SubSamplingImageSource.file(cached.data)
//        }
//        resource.dataSource.let { it == DataSource.DISK || it == DataSource.MEMORY_CACHE } -> when {
//          requestData is Uri -> SubSamplingImageSource.contentUri(requestData)
//          requestData is String -> SubSamplingImageSource.contentUri(Uri.parse(requestData))
//          resource.request.context.isResourceId(requestData) -> SubSamplingImageSource.resource(requestData)
//          else -> null
//        }
//        else -> null
//      }
//
//      if (imageSource != null) {
//        return imageSource
//      }
    }

    return null
  }

//  private fun ImageResult.crossfadeDuration(): Duration {
//    val transitionFactory = request.transitionFactory
//    return if (this is SuccessResult && transitionFactory is CrossfadeTransition.Factory) {
//      // I'm intentionally not using factory.create() because it optimizes crossfade duration
//      // to zero if the image was fetched from memory cache. SubSamplingImage will only read
//      // bitmaps from the disk so there will always be some delay in showing the image.
//      transitionFactory.durationMillis.milliseconds
//    } else {
//      Duration.ZERO
//    }
//  }
}

private fun Drawable.asPainter(): Painter {
  return DrawablePainter(mutate())
}

@VisibleForTesting
internal object EmptyPainter : Painter() {
  override val intrinsicSize: Size get() = Size.Unspecified
  override fun DrawScope.onDraw() = Unit
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
