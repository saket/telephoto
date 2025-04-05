package me.saket.telephoto.sample.crop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import me.saket.telephoto.sample.CropImageScreenKey
import me.saket.telephoto.sample.viewer.CropHandles
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@Composable
internal fun CropImageScreen(key: CropImageScreenKey) {
  Scaffold(
    contentWindowInsets = WindowInsets(0),
    containerColor = Color.Black,
  ) { contentPadding ->
    Box {
      val imageState = rememberZoomableImageState()
      ZoomableAsyncImage(
        modifier = Modifier.fillMaxSize(),
        state = imageState,
        model = ImageRequest.Builder(LocalContext.current)
          .data(key.mediaItem.fullSizedUrl)
          .placeholderMemoryCacheKey(key.mediaItem.placeholderImageUrl)
          .build(),
        contentDescription = key.mediaItem.caption,
      )

      AnimatedVisibility(
        modifier = Modifier
          .align(Alignment.Center)
          .padding(contentPadding),
        visible = !imageState.isImageDisplayed
      ) {
        CircularProgressIndicator(color = Color.White)
      }

      CropHandles(
        Modifier.fillMaxSize()
      )
    }
  }
}
