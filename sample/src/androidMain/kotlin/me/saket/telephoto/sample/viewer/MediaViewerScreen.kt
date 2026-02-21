package me.saket.telephoto.sample.viewer

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.sharedelements.SharedElementTransitionScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.flick.FlickToDismiss
import me.saket.telephoto.flick.FlickToDismissState
import me.saket.telephoto.flick.FlickToDismissState.RubberBandingSpec
import me.saket.telephoto.flick.rememberFlickToDismissState
import me.saket.telephoto.sample.CropImageScreenKey
import me.saket.telephoto.sample.MediaViewerScreenKey
import me.saket.telephoto.sample.gallery.MediaItem
import me.saket.telephoto.sample.gallery.sharedElementTransitionSpring
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
internal fun MediaViewerScreen(
  key: MediaViewerScreenKey,
  navigator: Navigator,
) {
  SharedElementTransitionScope {
    val sharedElementKey = key.album.items[key.initialIndex]
    val animatedVisibilityScope = requireAnimatedScope(SharedElementTransitionScope.AnimatedScope.Navigation)
    val sharedContentState = rememberSharedContentState("container_${sharedElementKey.placeholderImageUrl}")

    Scaffold(
      modifier = Modifier.sharedBounds(
        sharedContentState = sharedContentState,
        animatedVisibilityScope = animatedVisibilityScope,
        boundsTransform = { _, _ -> sharedElementTransitionSpring<Rect>() },
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        clipInOverlayDuringTransition = OverlayClip(RectangleShape),
      ),
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
        beyondViewportPageCount = 0,  // todo: undo
      ) { pageNum ->
        MediaPage(
          modifier = Modifier.fillMaxSize(),
          model = key.album.items[pageNum],
          isActivePage = pagerState.settledPage == pageNum,
        )
      }

      TopAppBar(
        modifier = Modifier
          .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
          .then(
            if (sharedContentState.isMatchFound) {
              with(animatedVisibilityScope) {
                // renderInSharedTransitionScopeOverlay() does not clip the content to
                // the parent's OverlayClip. Mimic it manually using animateEnterExit().
                Modifier.animateEnterExit(
                  enter = expandHorizontally(
                    expandFrom = Alignment.Start,
                    animationSpec = sharedElementTransitionSpring(),
                  ) + slideInVertically(
                    animationSpec = sharedElementTransitionSpring(),
                    initialOffsetY = { it / 2 }
                  ),
                  exit = shrinkHorizontally(
                    shrinkTowards = Alignment.Start,
                    animationSpec = sharedElementTransitionSpring(),
                  ) + slideOutVertically(
                    animationSpec = sharedElementTransitionSpring(),
                    targetOffsetY = { it / 2 },
                  ),
                )
              }
            } else {
              // Do not animate when navigating to screens other than the gallery.
              Modifier
            }
          ),
        title = {},
        navigationIcon = { CloseNavIconButton() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        actions = {
          val activeMediaItem = key.album.items[pagerState.currentPage]
          if (activeMediaItem is MediaItem.Image) {
            IconButton(
              onClick = { navigator.goTo(CropImageScreenKey(activeMediaItem)) },
              colors = titleBarIconButtonColors(),
            ) {
              Icon(
                imageVector = Icons.Rounded.Crop,
                contentDescription = "Crop",
              )
            }
          }
        }
      )
    }
  }
}

@Composable
private fun CloseNavIconButton() {
  val backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
  IconButton(
    onClick = { backDispatcher.onBackPressed() },
    colors = titleBarIconButtonColors(),
  ) {
    Icon(Icons.Rounded.Close, contentDescription = "Go back")
  }
}

@Composable
private fun titleBarIconButtonColors() = IconButtonDefaults.iconButtonColors(
  containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.4f)
)

@Composable
@OptIn(ExperimentalTelephotoApi::class, ExperimentalSharedTransitionApi::class)
private fun SharedElementTransitionScope.MediaPage(
  model: MediaItem,
  isActivePage: Boolean,
  modifier: Modifier = Modifier,
) {
  val zoomableState = rememberZoomableState()
  val focusRequester = remember { FocusRequester() }

  val flickState = rememberFlickToDismissState(
    rotateOnDrag = false,
    rubberBandingSpec = RubberBandingSpec(),
  )
  CloseScreenOnFlickDismissEffect(flickState)

  FlickToDismiss(
    state = flickState,
    modifier = modifier.background(backgroundColorFor(flickState.gestureState)),
  ) {
    when (model) {
      is MediaItem.Image -> {
        val imageUrl by produceState(initialValue = model.placeholderImageUrl) {
          delay(2_000)
          this.value = model.fullSizedUrl
        }

        // TODO: handle errors here.
        val imageState = rememberZoomableImageState(zoomableState)
        ZoomableAsyncImage(
          modifier = Modifier
            .then(
              if (isActivePage) {
                Modifier.sharedBounds(
                  sharedContentState = rememberSharedContentState(model.placeholderImageUrl),
                  animatedVisibilityScope = requireAnimatedScope(SharedElementTransitionScope.AnimatedScope.Navigation),
                  boundsTransform = { _, _ -> sharedElementTransitionSpring<Rect>() },
                )
              } else {
                Modifier
              }
            )
            .fillMaxSize()
            .focusRequester(focusRequester),
          state = imageState,
          model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
//            .placeholderMemoryCacheKey(model.placeholderImageUrl)
            .crossfade(300)
            .build(),
          contentDescription = model.caption,
        )

        // Focus the image so that it can receive keyboard and mouse shortcut events.
        if (isActivePage) {
          LaunchedEffect(Unit) {
            focusRequester.requestFocus()
          }
        }

        AnimatedVisibility(
          modifier = Modifier.align(Alignment.Center),
          visible = !imageState.isImageDisplayed
        ) {
          CircularProgressIndicator(color = Color.White)
        }
      }
    }
  }

  if (flickState.gestureState is FlickToDismissState.GestureState.Dragging) {
    LaunchedEffect(Unit) {
      zoomableState.resetZoom()
    }
  }
  if (!isActivePage) {
    LaunchedEffect(Unit) {
      zoomableState.resetZoom(animationSpec = SnapSpec())
    }
  }
}

@Composable
private fun CloseScreenOnFlickDismissEffect(flickState: FlickToDismissState) {
  val backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
  val gestureState = flickState.gestureState

  if (gestureState is FlickToDismissState.GestureState.Dismissing) {
    LaunchedEffect(Unit) {
      withContext(NonCancellable) {
        // Let the content animate its dismissal for a moment before starting the back transition
        // because compose UI does not support content transformations during shared element transitions.
        // https://issuetracker.google.com/issues/421153547
        delay(gestureState.animationDuration)
        backDispatcher.onBackPressed()
      }
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
  return MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).copy(alpha = animatedAlpha)
}

private val WindowInsets.Companion.none: WindowInsets
  @Stable get() = WindowInsets(0)
