package me.saket.telephoto.sample.gallery

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.Animatable
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import androidx.palette.graphics.Target
import androidx.palette.graphics.get
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
  modifier: Modifier = Modifier,
  navigator: Navigator
) {
  LazyVerticalGrid(
    modifier = modifier,
    columns = GridCells.Adaptive(minSize = 140.dp),
    contentPadding = PaddingValues(4.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    itemsIndexed(items = album.items) { index, item ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(200.dp)
          .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
          .clickable { navigator.lfg(MediaViewerScreenKey(album, initialIndex = index)) }
,
        contentAlignment = Alignment.BottomStart
      ) {

        val scope = rememberCoroutineScope()
        val colorScheme = MaterialTheme.colorScheme
        val captionBackground = remember { Animatable(colorScheme.surface) }

        AsyncImage(
          modifier = Modifier
            .fillMaxSize(),
          model = ImageRequest.Builder(LocalContext.current)
            .data(item.placeholderImageUrl)
            .memoryCacheKey(item.placeholderImageUrl)
            .crossfade(300)
            .allowHardware(false)
            .listener(onSuccess = { _, result ->
              scope.launch {
                val accent = result.drawable.extractColor()
                if (accent != null) {
                  captionBackground.animateTo(accent)
                }
              }
            })
            .build(),
          contentDescription = item.caption,
          contentScale = ContentScale.Crop,
        )
        Box(
          Modifier
            .matchParentSize()
            .background(
              Brush.verticalGradient(
                0.5f to captionBackground.value.copy(alpha = 0f),
                1f to captionBackground.value,
              )
            )
        )
        Text(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          text = item.caption
        )
      }
    }
  }
}

private suspend fun Drawable.extractColor(): Color? {
  (this as? BitmapDrawable)?.let {
    val palette = withContext(Dispatchers.IO) {
      Palette.from(it.bitmap).generate()
    }
    val swatch = palette[Target.DARK_MUTED] ?: return null
    return Color(swatch.rgb)
  }
  return null
}
