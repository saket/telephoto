package me.saket.telephoto

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
  var result: PainterOrImageSource? by remember { mutableStateOf(null) }

  LaunchedEffect(imageSource) {
    // todo: this delays rendering of images by 1 frame.
    //  Implement RememberObserver in AsyncImageSource directly?
    imageSource.foo {
      result = it
    }
  }

  when (val result = result) {
    is PainterOrImageSource.PainterResult -> {
      Image(
        modifier = modifier,
        painter = result.painter,
        contentDescription = null,
        viewportState = viewportState,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }

    is PainterOrImageSource.ImageSourceResult -> {
      SubSamplingImage(
        modifier = modifier,
        state = rememberSubSamplingImageState(
          imageSource = result.source,
          viewportState = viewportState
        ),
        contentDescription = contentDescription,
        // todo: add these two params.
        //alpha = alpha,
        //colorFilter = colorFilter,
      )
    }

    null -> Box(modifier)
  }
}

fun interface AsyncImageSource {
  companion object {
    @Stable
    fun coil(request: ImageRequest): AsyncImageSource = CoilImageSource(request)

    @Stable
    fun painter(painter: Painter): AsyncImageSource = PainterAsyncImageSource(painter)
  }

  suspend fun foo(action: (PainterOrImageSource) -> Unit)

  @Immutable
  private data class PainterAsyncImageSource(
    val painter: Painter
  ) : AsyncImageSource {
    override suspend fun foo(action: (PainterOrImageSource) -> Unit) {
      action(PainterOrImageSource.PainterResult(painter))
    }
  }

  @Immutable
  private data class CoilImageSource(
    private val request: ImageRequest
  ) : AsyncImageSource {
    @OptIn(ExperimentalCoilApi::class)
    override suspend fun foo(action: (PainterOrImageSource) -> Unit) {
      check(request.diskCachePolicy.writeEnabled)

      // todo: use request.bitmapConfig?

      request.context.imageLoader.execute(
        request.newBuilder()
          .listener(
            onError = { _, result ->
              action(PainterOrImageSource.PainterResult(createDrawablePainter(result.drawable)))
            },
            onSuccess = { _, result ->
              if (result.drawable is BitmapDrawable) {
                val diskCache = request.context.imageLoader.diskCache!!
                action(
                  PainterOrImageSource.ImageSourceResult(
                    ImageSource.file(diskCache[result.diskCacheKey!!]!!.data)
                  )
                )
              } else {
                action(PainterOrImageSource.PainterResult(createDrawablePainter(result.drawable)))
              }
            }
          )
          .target(
            onStart = { placeholder ->
              action(PainterOrImageSource.PainterResult(createDrawablePainter(placeholder)))
            }
          )
          .build()
      )
    }
  }
}

// todo: painters need to be remembered for them to start working.
sealed interface PainterOrImageSource {
  @JvmInline
  value class PainterResult(val painter: Painter) : PainterOrImageSource

  @JvmInline
  value class ImageSourceResult(val source: ImageSource) : PainterOrImageSource
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
