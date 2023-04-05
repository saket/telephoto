package me.saket.telephoto.sample.viewer

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.saket.telephoto.sample.MediaViewerScreenKey
import me.saket.telephoto.sample.gallery.MediaItem
import me.saket.telephoto.zoomable.rememberZoomableState

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun MediaViewerScreen(key: MediaViewerScreenKey) {
  Scaffold { contentPadding ->
    val pagerState = rememberPagerState(initialPage = key.initialIndex)
    HorizontalPager(
      modifier = Modifier
        .padding(contentPadding)
        .fillMaxSize(),
      state = pagerState,
      pageCount = key.album.items.size,
      beyondBoundsPageCount = 1,
      pageSpacing = 16.dp,
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
  IconButton(onClick = { backDispatcher.onBackPressed() }) {
    Icon(Icons.Rounded.Close, contentDescription = "Go back")
  }
}

@Composable
private fun MediaPage(
  model: MediaItem,
  modifier: Modifier = Modifier,
  isActivePage: Boolean,
) {
  val zoomableState = rememberZoomableState(maxZoomFactor = 2f)
  Box(modifier) {
    when (model) {
      is MediaItem.NormalSizedLocalImage -> NormalSizedLocalImage(zoomableState)
      is MediaItem.NormalSizedRemoteImage -> NormalSizedRemoteImage(zoomableState)
      is MediaItem.SubSampledImage -> LargeImage(zoomableState)
    }
  }

  if (!isActivePage) {
    LaunchedEffect(Unit) {
      zoomableState.resetZoomAndPanImmediately()
    }
  }
}
