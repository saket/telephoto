package me.saket.telephoto.zoomable.coil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.imageLoader
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.scan
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import kotlin.time.Duration.Companion.seconds

/**
 * A zoomable image that can be loaded by Coil and displayed using
 * [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImage].
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
 * See [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImage] for full documentation of parameters.
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
  gesturesEnabled: Boolean = true,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
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
    gesturesEnabled = gesturesEnabled,
    onClick = onClick,
    onLongClick = onLongClick,
    clipToBounds = clipToBounds,
  )

  if (state.detectTooManyReloads) {
    DetectTooManyReloadsEffect(model)
  }
}

/**
 * A zoomable image that can be loaded by Coil and displayed using
 * [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImageSource].
 *
 * Example usage:
 *
 * ```kotlin
 * ZoomableImage(
 *   image = ZoomableImageSource.coil("https://example.com/image.jpg"),
 *   contentDescription = …
 * )
 *
 * ZoomableImage(
 *   image = ZoomableImageSource.coil(
 *     ImageRequest.Builder(LocalContext.current)
 *       .data("https://example.com/image.jpg")
 *       .build()
 *   ),
 *   contentDescription = …
 * )
 * ```
 */
@Composable
fun ZoomableImageSource.Companion.coil(
  model: Any?,
  imageLoader: ImageLoader = LocalContext.current.imageLoader
): ZoomableImageSource {
  return remember(model, imageLoader) {
    CoilImageSource(model, imageLoader)
  }
}

@Composable
@OptIn(FlowPreview::class)
private fun DetectTooManyReloadsEffect(imageModel: Any?) {
  val modelChangeCount by remember { mutableIntStateOf(0) }.also {
    LaunchedEffect(imageModel) {
      it.intValue++
    }
  }
  val recompositionCount by remember { mutableIntStateOf(0) }.also {
    SideEffect {
      it.intValue++
    }
  }
  LaunchedEffect(Unit) {
    snapshotFlow { modelChangeCount }
      .sample(3.seconds)
      .scan(initial = 0) { acc, current -> current - acc }
      .collect { changesWithinDuration ->
        if (changesWithinDuration > 40 && recompositionCount >= 35) {
          throw TooManyReloadsException()
        }
      }
  }
}

internal class TooManyReloadsException : IllegalStateException(
  """|Too many image reloads were detected within a short period of time. 
     |This is an indication that the image model is changing _distinctly_ on every recomposition. 
     |
     |Read more about it here: 
     |https://saket.github.io/telephoto/zoomableimage/debugging/#too-many-image-reloads
     |
     """.trimMargin()
)
