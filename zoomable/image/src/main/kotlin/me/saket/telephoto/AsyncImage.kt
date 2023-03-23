package me.saket.telephoto

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
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
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.accompanist.drawablepainter.DrawablePainter
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomableViewportState

@Composable
fun AsyncImage(
  imageSource: AsyncImageSource,
  viewportState: ZoomableViewportState,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
) {
  val content by key(imageSource) {
    imageSource.content()
  }
  when (val it = content) {
    is ImageContent.PainterContent -> {
      Image(
        modifier = modifier,
        painter = it.painter,
        viewportState = viewportState,
        contentDescription = contentDescription,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }

    is ImageContent.BitmapContent -> {
      SubSamplingImage(
        modifier = modifier,
        state = rememberSubSamplingImageState(
          imageSource = it.source,
          viewportState = viewportState
        ),
        contentDescription = contentDescription,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }

    null -> Box(modifier)
  }
}

abstract class AsyncImageSource {
  companion object {
    @Stable
    fun coil(request: ImageRequest): AsyncImageSource = CoilImageSource(request)

    @Stable
    fun painter(painter: Painter): AsyncImageSource = PainterAsyncImageSource(painter)
  }

  @Composable
  internal abstract fun content(): State<ImageContent?>

  @Immutable
  private data class PainterAsyncImageSource(
    val painter: Painter
  ) : AsyncImageSource() {
    @Composable
    override fun content(): State<ImageContent> {
      return remember(painter) {
        stateOf(ImageContent.PainterContent(painter))
      }
    }
  }

  @Immutable
  private data class CoilImageSource(
    private val request: ImageRequest
  ) : AsyncImageSource() {
    @Composable
    @OptIn(ExperimentalCoilApi::class)
    override fun content(): State<ImageContent?> {
      return produceState(initialValue = null as ImageContent?, key1 = request) {
        val result = request.context.imageLoader.execute(
          request.newBuilder()
            // This will unfortunately replace any existing target, but it is the only
            // way to read placeholder images set using ImageRequest#placeholderMemoryCacheKey.
            // Placeholder images should be small in size so sub-sampling isn't needed here.
            .target(
              onStart = { placeholder ->
                this.value = ImageContent.PainterContent(createDrawablePainter(placeholder))
              }
            )
            .build()
        )

        // todo: use request.bitmapConfig?
        if (result is SuccessResult && result.drawable is BitmapDrawable) {
          val diskCache = request.context.imageLoader.diskCache ?: error("Disk caching must be enabled")
          val diskCacheKey = result.diskCacheKey ?: error("Disk caching must be enabled")
          val cached = diskCache[diskCacheKey] ?: error("Coil returned a null image from disk cache")
          this.value = ImageContent.BitmapContent(ImageSource.file(cached.data))

        } else {
          this.value = ImageContent.PainterContent(createDrawablePainter(result.drawable))
        }
      }
    }
  }
}

internal sealed interface ImageContent {
  @JvmInline
  value class PainterContent(
    val painter: Painter
  ) : ImageContent

  data class BitmapContent(
    val source: ImageSource,
  ) : ImageContent
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
