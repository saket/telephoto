@file:Suppress("NAME_SHADOWING")

package me.saket.telephoto.zoomable.coil3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.compose.DefaultModelEqualityDelegate
import coil3.compose.EqualityDelegate
import coil3.imageLoader
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/**
 * A zoomable image that can be loaded by Coil 3.
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
@NonRestartableComposable
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
  onDoubleClick: DoubleClickToZoomListener = DoubleClickToZoomListener.cycle(),
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
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
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
  val model = StableModel(model, equalityDelegate = DefaultModelEqualityDelegate)
  return remember(model, imageLoader) {
    Coil3ImageSource(model.model, imageLoader)
  }
}

/**
 * Adapted from Coil's AsyncImageState. Prevents relaunching a new image request when
 * `ImageRequest#listener`, `placeholder` or `target` change.
 */
@Stable
private class StableModel(
  val model: Any?,
  private val equalityDelegate: EqualityDelegate,
) {
  override fun equals(other: Any?): Boolean =
    equalityDelegate.equals(model, (other as? StableModel)?.model)

  override fun hashCode(): Int =
    equalityDelegate.hashCode(model)
}
