package me.saket.telephoto.sample

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.glide.ZoomableGlideImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

/**
 * Source compatibility test - must compile on both old and new commits without changes.
 *
 * This file exercises all public APIs that are affected by the MutableInteractionSource changes.
 * Created on commit 8d3c7852 (before MutableInteractionSource), it should continue to compile
 * on HEAD without any modifications, proving source compatibility.
 */
@Suppress("unused", "UNUSED_PARAMETER")
object CompatibilityTest {

  @Composable
  fun testModifierZoomable() {
    val state = rememberZoomableState()

    Modifier.zoomable(
      state = state,
      gestures = EnabledZoomGestures.ZoomAndPan,
      onClick = { _: Offset -> },
      onLongClick = { _: Offset -> },
      onDoubleClick = DoubleClickToZoomListener.cycle(),
      clipToBounds = true,
    )

    Modifier.zoomable(
      state = state,
      onClick = { _: Offset -> },
      onLongClick = { _: Offset -> },
      clipToBounds = true,
      onDoubleClick = DoubleClickToZoomListener.cycle(),
    )

    @Suppress("DEPRECATION")
    Modifier.zoomable(
      state = state,
      enabled = true,
      onClick = { _: Offset -> },
      onLongClick = { _: Offset -> },
      clipToBounds = true,
      onDoubleClick = DoubleClickToZoomListener.cycle(),
    )
  }

  @Composable
  fun testZoomableImage(imageSource: ZoomableImageSource) {
    val state = rememberZoomableImageState(rememberZoomableState())

    me.saket.telephoto.zoomable.ZoomableImage(
      image = imageSource,
      contentDescription = "Test",
      gestures = EnabledZoomGestures.ZoomAndPan,
      modifier = Modifier,
      state = state,
      onClick = { _: Offset -> },
      onLongClick = { _: Offset -> },
      onDoubleClick = DoubleClickToZoomListener.cycle(),
      clipToBounds = true,
      contentPadding = PaddingValues(0.dp),
    )

    me.saket.telephoto.zoomable.ZoomableImage(
      image = imageSource,
      contentDescription = "Test",
      modifier = Modifier,
      state = state,
      onClick = { _: Offset -> },
      onLongClick = { _: Offset -> },
      clipToBounds = true,
      onDoubleClick = DoubleClickToZoomListener.cycle(),
      contentPadding = PaddingValues(0.dp),
    )

    @Suppress("DEPRECATION")
    me.saket.telephoto.zoomable.ZoomableImage(
      image = imageSource,
      contentDescription = "Test",
      modifier = Modifier,
      state = state,
      gesturesEnabled = true,
      onClick = { _: Offset -> },
      onLongClick = { _: Offset -> },
      clipToBounds = true,
      onDoubleClick = DoubleClickToZoomListener.cycle(),
      contentPadding = PaddingValues(0.dp),
    )
  }

  @Composable
  fun testZoomableAsyncImageCoil(model: Any?, imageLoader: ImageLoader) {
    val state = rememberZoomableImageState(rememberZoomableState())

    ZoomableAsyncImage(
      model = model,
      contentDescription = "Test",
      gestures = EnabledZoomGestures.ZoomAndPan,
      modifier = Modifier,
      state = state,
      imageLoader = imageLoader,
      onClick = { _: Offset -> },
      onLongClick = { _: Offset -> },
      onDoubleClick = DoubleClickToZoomListener.cycle(),
      clipToBounds = true,
      contentPadding = PaddingValues(0.dp),
    )

    ZoomableAsyncImage(
      model = model,
      contentDescription = "Test",
      modifier = Modifier,
      state = state,
      imageLoader = imageLoader,
      onClick = { _: Offset -> },
      onLongClick = { _: Offset -> },
      clipToBounds = true,
      onDoubleClick = DoubleClickToZoomListener.cycle(),
      contentPadding = PaddingValues(0.dp),
    )

    @Suppress("DEPRECATION")
    ZoomableAsyncImage(
      model = model,
      contentDescription = "Test",
      modifier = Modifier,
      state = state,
      imageLoader = imageLoader,
      gesturesEnabled = true,
      onClick = { _: Offset -> },
      onLongClick = { _: Offset -> },
      clipToBounds = true,
      onDoubleClick = DoubleClickToZoomListener.cycle(),
      contentPadding = PaddingValues(0.dp),
    )
  }

  @Composable
  fun testZoomableGlideImage(model: Any?) {
    val state = rememberZoomableImageState(rememberZoomableState())

    ZoomableGlideImage(
      model = model,
      contentDescription = "Test",
      gestures = EnabledZoomGestures.ZoomAndPan,
      modifier = Modifier,
      state = state,
      onClick = { _: Offset -> },
      onLongClick = { _: Offset -> },
      onDoubleClick = DoubleClickToZoomListener.cycle(),
      clipToBounds = true,
      contentPadding = PaddingValues(0.dp),
      requestBuilderTransform = { it },
    )

    ZoomableGlideImage(
      model = model,
      contentDescription = "Test",
      modifier = Modifier,
      state = state,
      onClick = { _: Offset -> },
      onLongClick = { _: Offset -> },
      clipToBounds = true,
      onDoubleClick = DoubleClickToZoomListener.cycle(),
      contentPadding = PaddingValues(0.dp),
      requestBuilderTransform = { it },
    )

    @Suppress("DEPRECATION")
    ZoomableGlideImage(
      model = model,
      contentDescription = "Test",
      modifier = Modifier,
      state = state,
      gesturesEnabled = true,
      onClick = { _: Offset -> },
      onLongClick = { _: Offset -> },
      clipToBounds = true,
      onDoubleClick = DoubleClickToZoomListener.cycle(),
      contentPadding = PaddingValues(0.dp),
      requestBuilderTransform = { it },
    )
  }
}
