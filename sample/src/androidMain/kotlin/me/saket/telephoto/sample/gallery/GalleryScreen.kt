package me.saket.telephoto.sample.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.slack.circuit.runtime.Navigator
import me.saket.telephoto.sample.GalleryScreenKey
import me.saket.telephoto.sample.MediaViewerScreenKey
import me.saket.telephoto.sample.R
import me.saket.telephoto.zoomable.rememberZoomablePeekOverlayState
import me.saket.telephoto.zoomable.zoomablePeekOverlay

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun GalleryScreen(
  key: GalleryScreenKey,
  navigator: Navigator,
) {
  Scaffold(
    topBar = {
      TopAppBar(title = { Text(stringResource(R.string.app_name)) })
    }
  ) { contentPadding ->
    AlbumGrid(
      modifier = Modifier
        .padding(contentPadding)
        .fillMaxSize(),
      album = key.album,
      navigator = navigator,
    )
  }
}

@Composable
private fun AlbumGrid(
  album: MediaAlbum,
  navigator: Navigator,
  modifier: Modifier = Modifier
) {
  LazyVerticalGrid(
    modifier = modifier,
    columns = GridCells.Adaptive(minSize = 160.dp),
    contentPadding = PaddingValues(4.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    itemsIndexed(items = album.items) { index, item ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(280.dp)
          .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
          .clickable { navigator.goTo(MediaViewerScreenKey(album, initialIndex = index)) }
          .zoomablePeekOverlay(rememberZoomablePeekOverlayState()),
        contentAlignment = Alignment.BottomStart
      ) {
        AsyncImage(
          modifier = Modifier.fillMaxSize(),
          model = ImageRequest.Builder(LocalContext.current)
            .data(item.placeholderImageUrl)
            .memoryCacheKey(item.placeholderImageUrl)
            .crossfade(300)
            .build(),
          contentDescription = item.caption,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}
