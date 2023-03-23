package me.saket.telephoto

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import coil.annotation.ExperimentalCoilApi
import coil.decode.DataSource
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.accompanist.drawablepainter.DrawablePainter
import me.saket.telephoto.ZoomableImageSource.ImageContent.BitmapContent
import me.saket.telephoto.ZoomableImageSource.ImageContent.PainterContent
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomableViewportState

@Composable
fun Image(
  imageSource: ZoomableImageSource,
  zoomState: ZoomableViewportState,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
) {
  val content by key(imageSource) {
    imageSource.content()
  }
  when (val it = content) {
    is PainterContent -> {
      Image(
        modifier = modifier,
        painter = it.painter,
        zoomState = zoomState,
        contentDescription = contentDescription,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }

    is BitmapContent -> {
      SubSamplingImage(
        modifier = modifier,
        state = rememberSubSamplingImageState(
          imageSource = it.source,
          viewportState = zoomState
        ),
        contentDescription = contentDescription,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }

    null -> Box(modifier)
  }
}

abstract class ZoomableImageSource {
  companion object {
    @Stable
    fun coil(request: ImageRequest): ZoomableImageSource = CoilImageRequestSource(request)

    @Stable
    fun coil(model: Any?): ZoomableImageSource = CoilImageModelSource(model)

    @Stable
    fun painter(painter: Painter): ZoomableImageSource = PainterImageSource(painter)
  }

  @Composable
  internal abstract fun content(): State<ImageContent?>

  internal sealed interface ImageContent {
    @JvmInline
    value class PainterContent(
      val painter: Painter
    ) : ImageContent

    data class BitmapContent(
      val source: ImageSource,
    ) : ImageContent
  }

  @Immutable
  private data class PainterImageSource(
    val painter: Painter
  ) : ZoomableImageSource() {
    @Composable
    override fun content(): State<ImageContent> {
      return remember {
        stateOf(PainterContent(painter))
      }
    }
  }

  @Immutable
  private data class CoilImageModelSource(
    private val model: Any?
  ) : ZoomableImageSource() {
    @Composable
    override fun content(): State<ImageContent?> {
      val context = LocalContext.current
      val delegate = remember {
        CoilImageRequestSource(
          ImageRequest.Builder(context)
            .data(model)
            .build()
        )
      }
      return delegate.content()
    }
  }

  @Immutable
  private data class CoilImageRequestSource(
    private val request: ImageRequest
  ) : ZoomableImageSource() {
    @Composable
    @OptIn(ExperimentalCoilApi::class)
    override fun content(): State<ImageContent?> {
      return produceState(
        initialValue = null as ImageContent?,
        key1 = request
      ) {
        val result = request.context.imageLoader.execute(
          request.newBuilder()
            // There's no easy way to know whether an image will require sub-sampling in
            // advance so assume it'll be needed and the image will be read from the disk.
            .diskCachePolicy(
              when (request.diskCachePolicy) {
                CachePolicy.ENABLED -> CachePolicy.ENABLED
                CachePolicy.READ_ONLY -> CachePolicy.ENABLED
                CachePolicy.WRITE_ONLY,
                CachePolicy.DISABLED -> CachePolicy.WRITE_ONLY
              }
            )
            // This will unfortunately replace any existing target, but it is the only
            // way to read placeholder images set using ImageRequest#placeholderMemoryCacheKey.
            // Placeholder images should be small in size so sub-sampling isn't needed here.
            .target(
              onStart = { placeholder ->
                this.value = PainterContent(createDrawablePainter(placeholder))
              }
            )
            .build()
        )

        // todo: use request.bitmapConfig?
        this.value = if (result is SuccessResult && result.drawable is BitmapDrawable) {
          if (result.diskCacheKey != null) {
            // Prefer reading of images directly from files whenever possible because
            // that is significantly faster than reading from their input streams.
            val diskCache = request.context.imageLoader.diskCache!!
            val cached = diskCache[result.diskCacheKey!!] ?: error("Coil returned a null image from disk cache")
            BitmapContent(ImageSource.file(cached.data))

          } else if (result.dataSource == DataSource.DISK && result.request.data is Uri) {
            // Image is present on disk, but wasn't stored to disk cache.
            // Possibly an asset, a file, or a resource?
            BitmapContent(ImageSource.contentUri(result.request.data as Uri))

          } else {
            PainterContent(createDrawablePainter(result.drawable))
          }

        } else {
          PainterContent(createDrawablePainter(result.drawable))
        }
      }
    }
  }
}

private fun createDrawablePainter(drawable: Drawable?): Painter {
  return when (drawable) {
    null -> EmptyPainter
    is ColorDrawable -> ColorPainter(Color(drawable.color))
    else -> DrawablePainter(drawable.mutate())
  }
}

private object EmptyPainter : Painter() {
  override val intrinsicSize: Size get() = Size.Unspecified
  override fun DrawScope.onDraw() = Unit
}

private fun <T> stateOf(value: T): State<T> {
  return ImmutableState(value)
}

@Immutable
private class ImmutableState<T>(override val value: T) : State<T>
