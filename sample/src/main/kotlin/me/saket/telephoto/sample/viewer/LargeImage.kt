@file:Suppress("NAME_SHADOWING")

package me.saket.telephoto.sample.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomableViewportState

@Composable
@OptIn(ExperimentalCoilApi::class)
fun LargeImage(viewportState: ZoomableViewportState) {
  val context = LocalContext.current
  var imageSource: ImageSource? by remember { mutableStateOf(null) }

  LaunchedEffect(Unit) {
    val url = "https://images.unsplash.com/photo-1678465952838-c9d7f5daaa65"
    val result = context.imageLoader.execute(
      ImageRequest.Builder(context)
        .data(url)
        .memoryCachePolicy(CachePolicy.DISABLED)  // In-memory caching will be handled by SubSamplingImage.
        .build()
    )
    if (result is SuccessResult) {
      imageSource = ImageSource.stream {
        val diskCache = context.imageLoader.diskCache!!
        diskCache.fileSystem.source(diskCache[result.diskCacheKey!!]!!.data)
      }
    } else {
      // TODO: handle errors here.
    }
  }

  imageSource?.let { imageSource ->
    SubSamplingImage(
      modifier = Modifier.fillMaxSize(),
      state = rememberSubSamplingImageState(
        imageSource = imageSource,
        viewportState = viewportState,
      ),
      contentDescription = null,
    )
  }
}
