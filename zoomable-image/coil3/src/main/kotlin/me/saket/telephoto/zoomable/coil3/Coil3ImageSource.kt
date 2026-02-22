@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

package me.saket.telephoto.zoomable.coil3

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.compose.asPainter
import coil3.decode.DataSource
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.maxBitmapSize
import coil3.request.transitionFactory
import coil3.size.Dimension
import coil3.size.Precision
import coil3.size.SizeResolver
import coil3.toAndroidUri
import coil3.transition.CrossfadeTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.util.canBeSubSampled
import me.saket.telephoto.subsamplingimage.util.exists
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableImageSource.ResolveResult
import me.saket.telephoto.zoomable.copy
import me.saket.telephoto.zoomable.internal.RememberWorker
import java.io.File
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import coil3.size.Size as CoilSize

@Immutable
internal data class Coil3ImageSource(
  private val models: Flow<Any?>,
  private val imageLoaders: Flow<ImageLoader>,
) : ZoomableImageSource {

  @Composable
  override fun resolve(canvasSize: Flow<Size>): ResolveResult {
    val context = LocalContext.current
    val resolver = remember(this) {
      val requests = models.map { model ->
        model as? ImageRequest
          ?: ImageRequest.Builder(context)
            .data(model)
            .build()
      }
      Resolver(
        requests = requests,
        imageLoaders = imageLoaders,
        sizeResolver = { canvasSize.first().toCoilSize() },
      )
    }
    return resolver.resolved
  }

  private fun Size.toCoilSize() = CoilSize(
    width = if (width.isFinite()) Dimension(width.roundToInt()) else Dimension.Undefined,
    height = if (height.isFinite()) Dimension(height.roundToInt()) else Dimension.Undefined
  )
}

internal class Resolver(
  private val requests: Flow<ImageRequest>,
  private val imageLoaders: Flow<ImageLoader>,
  private val sizeResolver: SizeResolver,
) : RememberWorker() {

  internal var resolved: ResolveResult by mutableStateOf(
    ResolveResult(delegate = null)
  )

  override suspend fun work() {
    combine(requests, imageLoaders, ::Pair).collectLatest { (request, imageLoader) ->
      work(
        request = request,
        imageLoader = imageLoader,
        retryAttempts = 0,
      )
    }
  }

  private suspend fun work(request: ImageRequest, imageLoader: ImageLoader, retryAttempts: Int) {
    val result = imageLoader.execute(
      request.newBuilder()
        .size(request.defined.sizeResolver ?: sizeResolver)
        // There's no easy way to be certain whether an image will require sub-sampling in
        // advance so assume it'll be needed and force Coil to write this image to disk.
        .diskCachePolicy(
          when (request.diskCachePolicy) {
            CachePolicy.ENABLED -> CachePolicy.ENABLED
            CachePolicy.READ_ONLY -> CachePolicy.ENABLED
            CachePolicy.WRITE_ONLY,
            CachePolicy.DISABLED -> CachePolicy.WRITE_ONLY
          }
        )
        .memoryCachePolicy(
          if (retryAttempts > 0) CachePolicy.WRITE_ONLY else request.memoryCachePolicy
        )
        // This will unfortunately replace any existing target, but it is also the only
        // way to read placeholder images set using ImageRequest#placeholderMemoryCacheKey.
        // Placeholder images should be small in size so sub-sampling isn't needed here.
        .target(
          onStart = {
            resolved = resolved.copy(
              placeholder = it?.asPainter(request.context),
            )
          }
        )
        // Increase memory cache hit rate because the image will anyway fit the canvas
        // size at draw time.
        .precision(
          when (request.defined.precision) {
            Precision.EXACT -> request.precision
            else -> Precision.INEXACT
          }
        )
        // While telephoto will take care of loading the full-sized image, let Coil downsize
        // this image since there is still a possibility that the image may not be saved to
        // disk if (e.g., if Cache-Control HTTP headers prevent disk caching).
        .maxBitmapSize(CoilSize.ORIGINAL)
        .build()
    )

    val imageSource = result.toSubSamplingImageSource(imageLoader)
    if (imageSource == null && retryAttempts < 1 && result is SuccessResult && result.image is BitmapImage) {
      // Sub-sampling isn't available. This can happen if:
      // - The image was served from memory cache, but the disk cache was cleared.
      // - The disk cache entry was evicted between Coil writing it and telephoto reading it.
      // Retry once to re-populate it before falling back to a non-sub-sampled image.
      work(request, imageLoader, retryAttempts = retryAttempts + 1)
      return
    }

    resolved = resolved.copy(
      crossfadeDuration = result.crossfadeDuration(),
      delegate = if (result is SuccessResult && imageSource != null) {
        ZoomableImageSource.SubSamplingDelegate(
          source = imageSource,
          imageOptions = ImageBitmapOptions(from = (result.image as BitmapImage).bitmap)
        )
      } else {
        ZoomableImageSource.PainterDelegate(
          painter = result.image?.asPainter(request.context)
        )
      },
    )
  }

  private suspend fun ImageResult.toSubSamplingImageSource(imageLoader: ImageLoader): SubSamplingImageSource? {
    val result = this
    val resultImage = (result as? SuccessResult)?.image
    val source = if (result is SuccessResult && resultImage is BitmapImage) {
      val preview = resultImage.bitmap.asImageBitmap()
      when {
        // Prefer reading of images directly from files whenever possible because
        // it is significantly faster than reading from their input streams.
        result.diskCacheKey != null -> {
          val diskCache = imageLoader.diskCache!!
          val snapshot = withContext(Dispatchers.IO) {  // IO because openSnapshot can delete files.
            diskCache.openSnapshot(result.diskCacheKey!!)
          }
          if (snapshot == null) {
            null
          } else {
            SubSamplingImageSource.file(snapshot.data, preview, onClose = snapshot::close)
          }
        }

        result.dataSource.let { it == DataSource.DISK || it == DataSource.MEMORY_CACHE } -> {
          // Possible reasons for reaching this code path:
          // - Locally stored images such as assets, resource, etc.
          // - Remote image that wasn't saved to disk because of a "no-store" HTTP header.
          result.request.mapRequestDataToUriOrNull(imageLoader)
            ?.let { uri -> SubSamplingImageSource.contentUriOrNull(uri, preview) }
            ?.takeIf {
              result.dataSource != DataSource.MEMORY_CACHE || it.exists(request.context)
            }
        }

        else -> {
          // Image wasn't saved to the disk. Telephoto won't be able to load this image in its full
          // quality. It'll attempt to display the bitmap directly as a fallback, but that can
          // potentially cause an OutOfMemoryError when the bitmap is drawn.
          return null
        }
      }
    } else {
      return null
    }
    return source?.takeIf { it.canBeSubSampled(request.context) }
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

  private fun ImageRequest.mapRequestDataToUriOrNull(imageLoader: ImageLoader): Uri? {
    val dummyOptions = Options(this.context) // Good enough for mappers that only use Context.
    return when (val mapped = imageLoader.components.map(data, dummyOptions)) {
      is Uri -> mapped
      is File -> Uri.parse(mapped.path)
      is coil3.Uri -> mapped.toAndroidUri()
      else -> null
    }
  }
}
