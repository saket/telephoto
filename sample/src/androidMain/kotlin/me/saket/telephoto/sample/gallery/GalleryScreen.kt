package me.saket.telephoto.sample.gallery

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.sharedelements.SharedElementTransitionScope
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
@OptIn(ExperimentalSharedTransitionApi::class)
private fun AlbumGrid(
  album: MediaAlbum,
  navigator: Navigator,
  modifier: Modifier = Modifier
) {
  SharedElementTransitionScope {
    LazyVerticalStaggeredGrid(
      modifier = modifier,
      columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
      contentPadding = PaddingValues(4.dp),
      verticalItemSpacing = 4.dp,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      itemsIndexed(items = album.items) { index, item ->
        Box(
          modifier = Modifier
            .sharedBounds(
              sharedContentState = rememberSharedContentState("container_${item.placeholderImageUrl}"),
              animatedVisibilityScope = requireAnimatedScope(SharedElementTransitionScope.AnimatedScope.Navigation),
              boundsTransform = { _, _ -> sharedElementTransitionSpring<Rect>() },
              enter = EnterTransition.None,
              exit = ExitTransition.None,
            )
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
            .sharedElement(
              sharedContentState = rememberSharedContentState(item.placeholderImageUrl),
              animatedVisibilityScope = requireAnimatedScope(SharedElementTransitionScope.AnimatedScope.Navigation),
              boundsTransform = { _, _ -> sharedElementTransitionSpring<Rect>() },
            )
            .fillMaxWidth()
            .aspectRatio(item.aspectRatio)
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
}

internal inline fun <reified T> sharedElementTransitionSpring() =
  spring<T>(stiffness = (Spring.StiffnessMedium + Spring.StiffnessMediumLow) / 2f)
