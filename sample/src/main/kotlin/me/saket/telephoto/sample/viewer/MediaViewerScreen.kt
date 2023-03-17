package me.saket.telephoto.sample.viewer

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import me.saket.telephoto.sample.MediaViewerScreenKey
import me.saket.telephoto.sample.gallery.MediaItem
import me.saket.telephoto.zoomable.ZoomableViewport
import me.saket.telephoto.zoomable.rememberZoomableViewportState

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun MediaViewerScreen(key: MediaViewerScreenKey) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {},
        navigationIcon = { CloseNavIconButton() }
      )
    }
  ) { contentPadding ->
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
        model = key.album.items[pageNum]
      )
    }
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
) {
  val viewportState = rememberZoomableViewportState(maxZoomFactor = 3f)
  ZoomableViewport(
    modifier = modifier,
    state = viewportState,
    contentScale = ContentScale.Inside,
    contentAlignment = Alignment.Center,
  ) {
    when (model) {
      is MediaItem.NormalSizedLocalImage -> NormalSizedLocalImage(viewportState)
      is MediaItem.NormalSizedRemoteImage -> NormalSizedRemoteImage(viewportState)
      is MediaItem.SubSampledImage -> SubSampledImage(viewportState)
    }
  }
}
