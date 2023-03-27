package me.saket.telephoto.zoomable

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
  val content = key(zoomableImage) {
    zoomableImage.content()
  }
  when (content) {
    is PainterContent -> {
      LaunchedEffect(content.painter.intrinsicSize) {
        viewportState.setContentLocation(
          ZoomableContentLocation.fitInsideAndCenterAligned(content.painter.intrinsicSize)
        )
      }
      Image(
        modifier = modifier.applyTransformation(viewportState.contentTransformation),
        painter = content.painter,
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
          imageSource = content.source,
          viewportState = viewportState
        ),
        contentDescription = contentDescription,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }
  }
}

// todo: doc.
interface ZoomableImageSource {
  companion object; // For extensions.

  @Composable
  fun content(): ImageContent

  sealed interface ImageContent {
    @JvmInline
    @Immutable
    value class PainterContent(val painter: Painter) : ImageContent

    @Immutable
    data class BitmapContent(val source: ImageSource) : ImageContent
  }
}
