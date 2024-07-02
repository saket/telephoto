package me.saket.telephoto.zoomable.glide

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
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
 * [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImage].
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
 *   model = "https://example.com/image.jpg",
 *   contentDescription = …
 * ) {
 *   it.thumbnail(…)
 *     .error(…)
 *     .transition(withCrossFade(1_000))
 *     .addListener(…)
 * }
 * ```
 *
 * See [ZoomableImage()][me.saket.telephoto.zoomable.ZoomableImage] for full documentation of parameters.
 *
 * @param requestBuilderTransform Used for applying image options to this composable's [RequestBuilder].
 */
@Composable
@NonRestartableComposable
fun ZoomableGlideImage(
  model: Any?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  gesturesEnabled: Boolean = true,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
  requestBuilderTransform: (RequestBuilder<Drawable>) -> RequestBuilder<Drawable> = { it },
) {
  ZoomableImage(
    image = ZoomableImageSource.glide(model, requestBuilderTransform),
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
 *   image = ZoomableImageSource.glide("https://example.com/image.jpg") {
 *     it.thumbnail(…)
 *       .error(…)
 *       .transition(withCrossFade(1_000))
 *       .addListener(…)
 *   },
 *   contentDescription = …
 * )
 * ```
 */
@Composable
fun ZoomableImageSource.Companion.glide(
  model: Any?,
  requestBuilder: (RequestBuilder<Drawable>) -> RequestBuilder<Drawable> = { it },
): ZoomableImageSource {
  check(model !is RequestBuilder<*>) {
    "'model' parameter can't be a RequestBuilder. Please use the 'requestBuilderTransform' parameter instead."
  }

  // Note to self: Glide's RequestBuilder uses unstable APIs and can't be used as a remember key.
  val context = LocalContext.current
  return remember(model) {
    val requestManager = Glide.with(context)
    GlideImageSource(
      requestManager = requestManager,
      request = requestBuilder(requestManager.load(model)).lock(),
      isVectorDrawable = model?.isVectorDrawable(context) == true
    )
  }
}

private fun Any.isVectorDrawable(context: Context): Boolean {
  if (this is Int) {
    val resourcePath = TypedValue().also {
      context.resources.getValue(this, it, /* resolveRefs = */ true)
    }
    return resourcePath.string.endsWith(".xml")
  }
  return false
}
