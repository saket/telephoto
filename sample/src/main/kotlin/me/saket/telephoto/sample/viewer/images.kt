package me.saket.telephoto.sample.viewer

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import coil.request.ImageRequest
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import me.saket.telephoto.sample.R
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.glide.ZoomableGlideImage
import me.saket.telephoto.zoomable.rememberZoomableImageState

@Composable
private fun thumbnailRequest(): RequestBuilder<Drawable> {
  return Glide.with(LocalContext.current)
    .load("https://images.unsplash.com/photo-1678465952838-c9d7f5daaa65?w=100")
}

@Composable
fun Gif(
  zoomableState: ZoomableState,
) {
  ZoomableGlideImage(
    modifier = Modifier.fillMaxSize(),
    model = thumbnailRequest()
      .thumbnail(Glide.with(LocalContext.current).load(R.drawable.fox_smol))
      .error(R.drawable.smol),
    state = rememberZoomableImageState(zoomableState),
    contentDescription = null,
  )
}

@Composable
fun NormalSizedRemoteImage(
  zoomableState: ZoomableState
) {
  ZoomableGlideImage(
    modifier = Modifier.fillMaxSize(),
    model = Glide.with(LocalContext.current)
      .load("https://images.unsplash.com/photo-1678465952838-c9d7f5daaa65")
      .placeholder(R.drawable.fox_smol)
      .error(R.drawable.smol)
      .skipMemoryCache(true)  // todo: remove
      .thumbnail(thumbnailRequest())
      .transition(withCrossFade()),
    state = rememberZoomableImageState(zoomableState),
    contentDescription = null,
  )
}

@Composable
fun LargeImage(
  zoomableState: ZoomableState
) {
  // TODO: handle errors here.
  // TODO: show loading.

  ZoomableAsyncImage(
    modifier = Modifier.fillMaxSize(),
    model = ImageRequest.Builder(LocalContext.current)
      .data("https://images.unsplash.com/photo-1678465952838-c9d7f5daaa65")
      .placeholderMemoryCacheKey("placeholder")
      .crossfade(1_000)
      .build(),
    state = rememberZoomableImageState(zoomableState),
    contentDescription = null,
  )
}
