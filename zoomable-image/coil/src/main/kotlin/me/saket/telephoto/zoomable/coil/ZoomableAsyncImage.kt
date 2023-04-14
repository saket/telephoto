package me.saket.telephoto.zoomable.coil

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.DataSource
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.transition.CrossfadeTransition
import coil.transition.TransitionTarget
import com.google.accompanist.drawablepainter.DrawablePainter
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import coil.size.Size.Companion as CoilSize

/**
 * A zoomable image that can be loaded by Coil and displayed using
 * [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImageSource].
 *
 * Example usages:
 *
 * ```kotlin
 * ZoomableAsyncImage(
 *   model = "https://example.com/image.jpg",
 *   contentDescription = …
 * )
 *
 * ZoomableAsyncImage(
 *   model = ImageRequest.Builder(LocalContext.current)
 *     .data("https://example.com/image.jpg")
 *     .build(),
 *   contentDescription = …
 * )
 * ```
 *
 * See [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImageSource]
 * for full documentation.
 */
@Composable
fun ZoomableAsyncImage(
  model: Any?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  imageLoader: ImageLoader = LocalContext.current.imageLoader,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
) {
  ZoomableImage(
    image = ZoomableImageSource.coil(model, imageLoader),
    contentDescription = contentDescription,
    modifier = modifier,
    state = state,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    onClick = onClick,
    onLongClick = onLongClick,
  )
}

/**
 * A zoomable image that can be loaded by Coil and displayed using
 * [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImageSource].
 *
 * Example usage:
 *
 * ```kotlin
 * ZoomableImage(
 *   image = ZoomableImage.coil("https://example.com/image.jpg"),
 *   contentDescription = …
 * )
 *
 * ZoomableImage(
 *   image = ZoomableImage.coil(
 *     ImageRequest.Builder(LocalContext.current)
 *       .data("https://example.com/image.jpg")
 *       .build()
 *   ),
 *   contentDescription = …
 * )
 * ```
 *
 * See [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImageSource]
 * for full documentation.
 */
@Composable
fun ZoomableImageSource.Companion.coil(
  model: Any?,
  imageLoader: ImageLoader = LocalContext.current.imageLoader
): ZoomableImageSource {
  val context = LocalContext.current
  val request = remember(model) {
    model as? ImageRequest
      ?: ImageRequest.Builder(context)
        .data(model)
        .build()
  }

  var resolved by remember(request) {
    mutableStateOf(
      ZoomableImageSource(
        source = null,
        placeholder = EmptyPainter,
        bitmapConfig = request.bitmapConfig,
      )
    )
  }

  LaunchedEffect(request) {
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
            resolved = resolved.copy(placeholder = it.asPainter())
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
        placeholder = result.drawable.asPainter(),
        source = null,
      )
    }
  }

  // todo: read color space.
  return resolved
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
  val fakeTransitionTarget = object : TransitionTarget {
    override val view get() = throw UnsupportedOperationException()
    override val drawable: Drawable? get() = null
  }

  val transition = request.transitionFactory.create(fakeTransitionTarget, this)
  return if (transition is CrossfadeTransition) transition.durationMillis.milliseconds else Duration.ZERO
}

private fun Drawable?.asPainter(): Painter {
  return if (this == null) EmptyPainter else DrawablePainter(mutate())
}

internal object EmptyPainter : Painter() {
  override val intrinsicSize: Size get() = Size.Unspecified
  override fun DrawScope.onDraw() = Unit
}

private val Drawable.intrinsicSize: Size
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
