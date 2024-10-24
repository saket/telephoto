@file:Suppress("NAME_SHADOWING")

package me.saket.telephoto.subsamplingimage

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapIndexedNotNull
import me.saket.telephoto.subsamplingimage.internal.ImageRegionTile
import me.saket.telephoto.subsamplingimage.internal.SubSamplingLayoutItemProvider
import me.saket.telephoto.subsamplingimage.internal.toCeilInt

/**
 * An Image composable that can render large bitmaps by diving them into tiles so that they
 * can be loaded lazily. This ensures that images maintain their intricate details even when
 * fully zoomed in, without causing any `OutOfMemory` exceptions.
 *
 * [SubSamplingImage] is automatically used by [ZoomableImage][me.saket.telephoto.zoomable.ZoomableImage].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubSamplingImage(
  state: SubSamplingImageState,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
) {
  check(state is RealSubSamplingImageState)

  // todo: remove
  state.showTileBounds = true

  val itemContent: @Composable (ImageRegionTile) -> Unit = { region ->
    val isDarkTheme = isSystemInDarkTheme()
    Box(
      modifier = Modifier
        .fillMaxSize()
        .then(
          if (state.showTileBounds) {
            Modifier.border(1.dp, if (isDarkTheme) Color.White else Color.Black)
          } else {
            Modifier
          }
        ),
      contentAlignment = Alignment.Center,
    ) {
      // todo:
      //   TEST: do not draw foreground tiles until the bitmap for all tiles are loaded
      val painter = state.loadImage(region)
      val fadeInAlpha = if (region.sampleSize.size == 2) 1f else animateFloatAsState(
        targetValue = if (painter == null) 0f else 1f,
        label = "bitmap alpha",
        animationSpec = tween(2_00), // todo: test
      ).value
      if (painter != null && state.isImageLoaded) {
        Image(
          painter = painter,
          contentDescription = null,
          modifier = Modifier.matchParentSize(),
          alignment = Alignment.Center,
          contentScale = ContentScale.FillBounds,
          alpha = alpha * fadeInAlpha, // todo: can this be animated?
          colorFilter = colorFilter,
        )
      }
    }
  }

  // todo: get rid of LazyLayout().
  LazyLayout(
    modifier = modifier
      .fillMaxSize()
      .contentDescription(contentDescription)
      // todo: fold this modifier into LazyLayout's measure policy.
      .wrapContentSizeIfNeeded(state.imageSize),
    itemProvider = {
      SubSamplingLayoutItemProvider(state, itemContent)
    },
  ) { constraints ->
    // todo: check for hasBoundedWidth and hasBoundedHeight.
    val viewportSize = IntSize(constraints.maxWidth, constraints.maxHeight)
    state.canvasSize = viewportSize

    val placeables = state.viewportTiles.fastMapIndexedNotNull { index, tile ->
      if (tile.isVisible) {
        measure(
          index = index,
          constraints = Constraints(maxWidth = tile.bounds.width, maxHeight = tile.bounds.height),
        ).single() to tile
      } else {
        null
      }
    }
    layout(viewportSize.width, viewportSize.height) {
      placeables.fastForEach { (placeable, tile) ->
        placeable.place(
          position = tile.bounds.topLeft,
        )
      }
    }
  }
}

@SuppressLint("ComposeParameterOrder")
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)  // For binary compatibility.
@Composable
fun SubSamplingImage(
  state: SubSamplingImageState,
  modifier: Modifier = Modifier,
  contentDescription: String?,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
) {
  SubSamplingImage(
    state,
    contentDescription,
    modifier,
    alpha,
    colorFilter
  )
}

@Stable
private fun Modifier.contentDescription(contentDescription: String?): Modifier {
  if (contentDescription != null) {
    return semantics {
      this.contentDescription = contentDescription
      this.role = Role.Image
    }
  } else {
    return this
  }
}

@Stable
@Suppress("NAME_SHADOWING")
private fun Modifier.wrapContentSizeIfNeeded(imageSize: IntSize?): Modifier {
  if (imageSize == null) {
    return this
  }

  return layout { measurable, constraints ->
    val constraints = if (!constraints.hasFixedSize) {
      val scaleToFitImage = minOf(
        constraints.maxWidth / imageSize.width.toFloat(),
        constraints.maxHeight / imageSize.height.toFloat()
      ).coerceAtMost(1f)
      constraints.constrain(
        Constraints(
          minWidth = (scaleToFitImage * imageSize.width).toCeilInt(),
          minHeight = (scaleToFitImage * imageSize.height).toCeilInt()
        )
      )
    } else {
      constraints
    }
    val placeable = measurable.measure(constraints)
    layout(width = placeable.width, height = placeable.height) {
      placeable.place(IntOffset.Zero)
    }
  }
}

@Stable
private val Constraints.hasFixedSize
  get() = hasFixedWidth && hasFixedHeight
