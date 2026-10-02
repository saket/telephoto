@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "NAME_SHADOWING", "DEPRECATION")

package me.saket.telephoto.zoomable.coil

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.imageLoader
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/**
 * A zoomable image that can be loaded by Coil.
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
 * See [ZoomableImage()][ZoomableImage] for full documentation of parameters.
 */
@Composable
@NonRestartableComposable
fun ZoomableAsyncImage(
  model: Any?,
  contentDescription: String?,
  gestures: EnabledZoomGestures,
  modifier: Modifier = Modifier,
  placeholder: Painter? = null,
  error: Painter? = null,
  fallback: Painter? = error,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  imageLoader: ImageLoader = LocalContext.current.imageLoader,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  onDoubleClick: DoubleClickToZoomListener = DoubleClickToZoomListener.cycle(),
  clipToBounds: Boolean = true,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  interactionSource: MutableInteractionSource? = null,
) {
  ZoomableImage(
    image = ZoomableImageSource.coil(
      model = model,
      imageLoader = imageLoader,
      placeholder = placeholder,
      error = error,
      fallback = fallback,
    ),
    contentDescription = contentDescription,
    modifier = modifier,
    state = state,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    gestures = gestures,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
  )
}

@Composable
@NonRestartableComposable
fun ZoomableAsyncImage(
  model: Any?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  placeholder: Painter? = null,
  error: Painter? = null,
  fallback: Painter? = error,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  imageLoader: ImageLoader = LocalContext.current.imageLoader,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
  onDoubleClick: DoubleClickToZoomListener = DoubleClickToZoomListener.cycle(),
  contentPadding: PaddingValues = PaddingValues(0.dp),
  interactionSource: MutableInteractionSource? = null,
) {
  ZoomableAsyncImage(
    model = model,
    contentDescription = contentDescription,
    modifier = modifier,
    placeholder = placeholder,
    error = error,
    fallback = fallback,
    state = state,
    imageLoader = imageLoader,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    gestures = EnabledZoomGestures.ZoomAndPan,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
  )
}

@Deprecated(
  "Use the 'gestures' parameter instead. " +
    "Replace `gesturesEnabled = true` with `gestures = ZoomInteractions.ZoomAndPan`, " +
    "or `gesturesEnabled = false` with `gestures = ZoomInteractions.None`.",
  level = DeprecationLevel.HIDDEN,
)
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
  contentPadding: PaddingValues = PaddingValues(0.dp),
  interactionSource: MutableInteractionSource? = null,
) {
  ZoomableAsyncImage(
    model = model,
    contentDescription = contentDescription,
    modifier = modifier,
    state = state,
    imageLoader = imageLoader,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    gestures = if (gesturesEnabled) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
  )
}

/**
 * A zoomable image that can be loaded by Coil and displayed using
 * [ZoomableImage()][ZoomableImageSource].
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
  imageLoader: ImageLoader = LocalContext.current.imageLoader,
  placeholder: Painter? = null,
  error: Painter? = null,
  fallback: Painter? = error,
): ZoomableImageSource {
  val model = rememberUpdatedState(model)
  val imageLoader = rememberUpdatedState(imageLoader)
  val placeholder = rememberUpdatedState(placeholder)
  val error = rememberUpdatedState(error)
  val fallback = rememberUpdatedState(fallback)
  return remember {
    CoilImageSource(
      model = model,
      imageLoader = imageLoader,
      placeholder = placeholder,
      error = error,
      fallback = fallback,
    )
  }
}

@Composable
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
fun ZoomableImageSource.Companion.coil(
  model: Any?,
  imageLoader: ImageLoader = LocalContext.current.imageLoader,
): ZoomableImageSource {
  return coil(
    model = model,
    imageLoader = imageLoader,
    placeholder = null,
    error = null,
    fallback = null,
  )
}

@Composable
@NonRestartableComposable
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
fun ZoomableAsyncImage(
  model: Any?,
  contentDescription: String?,
  gestures: EnabledZoomGestures,
  modifier: Modifier = Modifier,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  imageLoader: ImageLoader = LocalContext.current.imageLoader,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  onDoubleClick: DoubleClickToZoomListener = DoubleClickToZoomListener.cycle(),
  clipToBounds: Boolean = true,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  interactionSource: MutableInteractionSource? = null,
) {
  ZoomableAsyncImage(
    model = model,
    contentDescription = contentDescription,
    gestures = gestures,
    modifier = modifier,
    placeholder = null,
    error = null,
    fallback = null,
    state = state,
    imageLoader = imageLoader,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
  )
}

@Composable
@NonRestartableComposable
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
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
  clipToBounds: Boolean = true,
  onDoubleClick: DoubleClickToZoomListener = DoubleClickToZoomListener.cycle(),
  contentPadding: PaddingValues = PaddingValues(0.dp),
  interactionSource: MutableInteractionSource? = null,
) {
  ZoomableAsyncImage(
    model = model,
    contentDescription = contentDescription,
    modifier = modifier,
    placeholder = null,
    error = null,
    fallback = null,
    state = state,
    imageLoader = imageLoader,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
  )
}

@Composable
@Suppress("unused")
@NonRestartableComposable
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
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
  ZoomableAsyncImage(
    model = model,
    contentDescription = contentDescription,
    modifier = modifier,
    state = state,
    imageLoader = imageLoader,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    gestures = if (gesturesEnabled) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
    contentPadding = PaddingValues(0.dp),
    interactionSource = null,
  )
}

@Composable
@Suppress("unused")
@NonRestartableComposable
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
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
  ZoomableAsyncImage(
    model = model,
    contentDescription = contentDescription,
    modifier = modifier,
    state = state,
    imageLoader = imageLoader,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    gestures = if (gesturesEnabled) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = DoubleClickToZoomListener.cycle(),
    clipToBounds = clipToBounds,
    contentPadding = PaddingValues(0.dp),
    interactionSource = null,
  )
}

@Composable
@NonRestartableComposable
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
fun ZoomableAsyncImage(
  model: Any?,
  contentDescription: String?,
  gestures: EnabledZoomGestures,
  modifier: Modifier = Modifier,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  imageLoader: ImageLoader = LocalContext.current.imageLoader,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  onDoubleClick: DoubleClickToZoomListener = DoubleClickToZoomListener.cycle(),
  clipToBounds: Boolean = true,
  contentPadding: PaddingValues = PaddingValues(0.dp),
) {
  ZoomableAsyncImage(
    model = model,
    contentDescription = contentDescription,
    gestures = gestures,
    modifier = modifier,
    state = state,
    imageLoader = imageLoader,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
    contentPadding = contentPadding,
    interactionSource = null,
  )
}

@Composable
@NonRestartableComposable
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
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
  clipToBounds: Boolean = true,
  onDoubleClick: DoubleClickToZoomListener = DoubleClickToZoomListener.cycle(),
  contentPadding: PaddingValues = PaddingValues(0.dp),
) {
  ZoomableAsyncImage(
    model = model,
    contentDescription = contentDescription,
    modifier = modifier,
    placeholder = null,
    error = null,
    fallback = null,
    state = state,
    imageLoader = imageLoader,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    gestures = EnabledZoomGestures.ZoomAndPan,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
    contentPadding = contentPadding,
    interactionSource = null,
  )
}

@Composable
@NonRestartableComposable
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
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
  contentPadding: PaddingValues = PaddingValues(0.dp),
) {
  ZoomableAsyncImage(
    model = model,
    contentDescription = contentDescription,
    modifier = modifier,
    state = state,
    imageLoader = imageLoader,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    gestures = if (gesturesEnabled) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
    contentPadding = contentPadding,
    interactionSource = null,
  )
}
