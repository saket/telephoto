package me.saket.telephoto.zoomable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomableImageSource.ImageContent.BitmapContent
import me.saket.telephoto.zoomable.ZoomableImageSource.ImageContent.PainterContent

// todo: doc.
@Composable
fun Image(
  zoomableImage: ZoomableImageSource,
  viewportState: ZoomableViewportState,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
) {
  val content by key(zoomableImage) {
    zoomableImage.content()
  }
  when (val it = content) {
    is PainterContent -> {
      LaunchedEffect(it.painter.intrinsicSize) {
        viewportState.setContentLocation(
          ZoomableContentLocation.fitInsideAndCenterAligned(it.painter.intrinsicSize)
        )
      }
      Image(
        modifier = modifier
          .fillMaxSize()
          .applyTransformation(viewportState.contentTransformation),
        painter = it.painter,
        contentDescription = contentDescription,
        alignment = Alignment.Center,
        contentScale = ContentScale.Inside,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }

    is BitmapContent -> {
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

fun interface ZoomableImageSource {
  companion object; // For extensions.

  @Composable
  fun content(): State<ImageContent?>

  sealed interface ImageContent {
    @JvmInline
    @Immutable
    value class PainterContent(val painter: Painter) : ImageContent

    @Immutable
    data class BitmapContent(val source: ImageSource) : ImageContent
  }
}
