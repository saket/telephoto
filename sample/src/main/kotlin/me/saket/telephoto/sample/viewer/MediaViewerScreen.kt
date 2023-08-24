package me.saket.telephoto.sample.viewer

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import me.saket.telephoto.flick.FlickToDismiss
import me.saket.telephoto.flick.FlickToDismissState
import me.saket.telephoto.flick.FlickToDismissState.GestureState.Dismissing
import me.saket.telephoto.flick.rememberFlickToDismissState
import me.saket.telephoto.sample.MediaViewerScreenKey
import me.saket.telephoto.sample.gallery.MediaItem
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun MediaViewerScreen(key: MediaViewerScreenKey) {
  Scaffold(
    contentWindowInsets = WindowInsets.none,
    containerColor = Color.Transparent,
    contentColor = Color.White,
  ) { contentPadding ->
    val pagerState = rememberPagerState(
      initialPage = key.initialIndex,
      pageCount = { key.album.items.size },
    )
    HorizontalPager(
      modifier = Modifier
        .padding(contentPadding)
        .fillMaxSize(),
      state = pagerState,
      beyondBoundsPageCount = 1,
      pageSpacing = 16.dp,
    ) { pageNum ->
      val flickState = rememberFlickToDismissState()
      CloseScreenOnFlickDismissEffect(flickState)

      FlickToDismiss(
        state = flickState,
        modifier = Modifier.background(
          MaterialTheme.colorScheme.background.copy(alpha = 1f - flickState.offsetFraction)
        )
      ) {
        MediaPage(
          modifier = Modifier.fillMaxSize(),
          model = key.album.items[pageNum],
          isActivePage = pagerState.settledPage == pageNum,
        )
      }
    }

    TopAppBar(
      title = {},
      navigationIcon = {
        // This can be uncommented once screen transitions are setup so
        // that this screen can smoothly fade-out when it's flick-dismissed.
        /*CloseNavIconButton()*/
      },
      colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
  }
}

@Composable
private fun CloseScreenOnFlickDismissEffect(flickState: FlickToDismissState) {
  val backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
  val gestureState = flickState.gestureState

  if (gestureState is Dismissing) {
    LaunchedEffect(Unit) {
      delay(gestureState.animationDuration / 2)
      backDispatcher.onBackPressed()
    }
  }
}

@Composable
private fun CloseNavIconButton() {
  val backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
  IconButton(
    onClick = { backDispatcher.onBackPressed() },
    colors = IconButtonDefaults.iconButtonColors(
      containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.4f)
    ),
  ) {
    Icon(Icons.Rounded.Close, contentDescription = "Go back")
  }
}

@Composable
private fun MediaPage(
  model: MediaItem,
  modifier: Modifier = Modifier,
  isActivePage: Boolean,
) {
  val zoomableState = rememberZoomableState()
  when (model) {
    is MediaItem.Image -> {
      // TODO: handle errors here.
      // TODO: show loading.
      ZoomableAsyncImage(
        modifier = modifier,
        state = rememberZoomableImageState(zoomableState),
        model = ImageRequest.Builder(LocalContext.current)
          .data(model.fullSizedUrl)
          .placeholderMemoryCacheKey(model.placeholderImageUrl)
          .crossfade(300)
          .build(),
        contentDescription = model.caption,
      )
    }
  }

  if (!isActivePage) {
    LaunchedEffect(Unit) {
      zoomableState.resetZoom(withAnimation = false)
    }
  }
}

private val WindowInsets.Companion.none: WindowInsets
  @Stable
  get() = WindowInsets(0, 0, 0, 0)
