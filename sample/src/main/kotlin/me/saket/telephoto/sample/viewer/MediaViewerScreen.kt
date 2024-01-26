package me.saket.telephoto.sample.viewer

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import me.saket.telephoto.flick.FlickToDismiss
import me.saket.telephoto.flick.FlickToDismissState
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
    contentColor = Color.White,
    containerColor = Color.Transparent,
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
    ) { pageNum ->
      MediaPage(
        modifier = Modifier.fillMaxSize(),
        model = key.album.items[pageNum],
        isActivePage = pagerState.settledPage == pageNum,
      )
    }

    TopAppBar(
      title = {},
      navigationIcon = { CloseNavIconButton() },
      colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
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
  val flickState = rememberFlickToDismissState(dismissThresholdRatio = 0.05f)
  CloseScreenOnFlickDismissEffect(flickState)

  FlickToDismiss(
    state = flickState,
    modifier = Modifier.background(backgroundColorFor(flickState.gestureState)),
  ) {
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
  }

  if (flickState.gestureState is FlickToDismissState.GestureState.Dragging) {
    LaunchedEffect(Unit) {
      zoomableState.resetZoom(withAnimation = true)
    }
  }
  if (!isActivePage) {
    LaunchedEffect(Unit) {
      zoomableState.resetZoom(withAnimation = false)
    }
  }
}

@Composable
private fun CloseScreenOnFlickDismissEffect(flickState: FlickToDismissState) {
  val backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
  val gestureState = flickState.gestureState

  if (gestureState is FlickToDismissState.GestureState.Dismissing) {
    LaunchedEffect(Unit) {
      // Schedule an exit in advance because there is a slight delay from when an exit
      // navigation is issued to when the screen actually hides from the UI. Gotta think
      // of a better way to do this. Could the flick animation integrate with the navigation
      // framework's exit transition?
      delay(gestureState.animationDuration / 2)
      backDispatcher.onBackPressed()
    }
  }
}

@Composable
private fun backgroundColorFor(flickGestureState: FlickToDismissState.GestureState): Color {
  val animatedAlpha by animateFloatAsState(
    targetValue = when (flickGestureState) {
      is FlickToDismissState.GestureState.Dismissed,
      is FlickToDismissState.GestureState.Dismissing -> 0f
      is FlickToDismissState.GestureState.Dragging -> if (flickGestureState.willDismissOnRelease) 0f else 1f
      is FlickToDismissState.GestureState.Idle,
      is FlickToDismissState.GestureState.Resetting -> 1f
    },
    label = "Background alpha",
  )
  return MaterialTheme.colorScheme.background.copy(alpha = animatedAlpha)
}

private val WindowInsets.Companion.none: WindowInsets
  @Stable
  get() = WindowInsets(0, 0, 0, 0)
