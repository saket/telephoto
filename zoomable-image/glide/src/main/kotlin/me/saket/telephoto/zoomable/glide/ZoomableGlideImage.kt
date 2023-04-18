package me.saket.telephoto.zoomable.glide

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/**
 * A zoomable image that can be loaded by Glide and displayed using
 * [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImageSource].
 *
 * Example usages:
 *
 * ```kotlin
 * ZoomableGlideImage(
 *   model = "https://example.com/image.jpg",
 *   contentDescription = …
 * )
 *
 * ZoomableGlideImage(
 *   model = Glide
 *     .with(LocalContext.current)
 *     .load("https://example.com/image.jpg"),
 *   contentDescription = …
 * )
 * ```
 *
 * See [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImageSource]
 * for full documentation.
 */
@Composable
fun ZoomableGlideImage(
  model: Any?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
) {
  ZoomableImage(
    image = ZoomableImageSource.glide(model),
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
 * A zoomable image that can be loaded by Glide and displayed using
 * [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImageSource].
 *
 * Example usage:
 *
 * ```kotlin
 * ZoomableImage(
 *   image = ZoomableImageSource.glide("https://example.com/image.jpg"),
 *   contentDescription = …
 * )
 *
 * ZoomableImage(
 *   image = ZoomableImageSource.glide(
 *     Glide
 *       .with(LocalContext.current)
 *       .load("https://example.com/image.jpg")
 *   ),
 *   contentDescription = …
 * )
 * ```
 *
 * See [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImageSource]
 * for full documentation.
 */
@Composable
@Suppress("UNCHECKED_CAST")
fun ZoomableImageSource.Companion.glide(model: Any?): ZoomableImageSource {
  val context = LocalContext.current
  val resolver = remember(model) {
    val requestManager = Glide.with(context)
    GlideImageResolver(
      request = model as? RequestBuilder<Drawable> ?: requestManager.load(model),
      requestManager = requestManager,
    )
  }
  return resolver.resolved
}
