package me.saket.telephoto.sample.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.saket.telephoto.sample.GalleryScreenKey
import me.saket.telephoto.sample.MediaViewerScreenKey
import me.saket.telephoto.sample.Navigator
import me.saket.telephoto.sample.R

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GalleryScreen(
  key: GalleryScreenKey,
  navigator: Navigator,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp)
        )
      )
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
  modifier: Modifier = Modifier,
  navigator: Navigator
) {
  LazyVerticalGrid(
    modifier = modifier,
    columns = GridCells.Adaptive(minSize = 140.dp),
    contentPadding = PaddingValues(2.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    itemsIndexed(items = album.items) { index, item ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 100.dp)
          .background(MaterialTheme.colorScheme.tertiaryContainer)
          .clickable { navigator.lfg(MediaViewerScreenKey(album, initialIndex = index)) }
          .padding(16.dp),
        contentAlignment = Alignment.BottomStart
      ) {
        Text(
          text = item.caption
        )
      }
    }
  }
}
