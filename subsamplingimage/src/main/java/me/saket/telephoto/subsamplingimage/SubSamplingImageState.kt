@file:Suppress("NAME_SHADOWING")
@file:OptIn(ExperimentalCoroutinesApi::class)

package me.saket.telephoto.subsamplingimage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import me.saket.telephoto.subsamplingimage.internal.BitmapSampleSize
import me.saket.telephoto.subsamplingimage.internal.BitmapTile
import me.saket.telephoto.subsamplingimage.internal.BitmapTileGrid
import me.saket.telephoto.subsamplingimage.internal.SkiaImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.calculateFor
import me.saket.telephoto.subsamplingimage.internal.generateBitmapTileGrid
import me.saket.telephoto.subsamplingimage.internal.overlaps
import me.saket.telephoto.zoomable.ZoomableState
import java.io.IOException

@Composable
fun rememberSubSamplingImageState(
  zoomableState: ZoomableState,
  imageSource: ImageSource,
  stateListener: SubSamplingImageEventListener = SubSamplingImageEventListener.Empty
): SubSamplingImageState {
  val context = LocalContext.current
  val stateListener by rememberUpdatedState(stateListener)

  val decoder by produceState<SkiaImageRegionDecoder?>(initialValue = null, key1 = imageSource) {
    try {
      value = SkiaImageRegionDecoder.create(context, imageSource).also {
        stateListener.onImageLoaded()
        zoomableState.setUnscaledContentSize(it.imageSize)
      }
    } catch (e: IOException) {
      stateListener.onImageLoadingFailed(e)
    }
  }

  val state = remember {
    SubSamplingImageState()
  }

  decoder?.let { decoder ->
    LaunchedEffect(state, zoomableState, decoder) {
      val transformations = snapshotFlow { zoomableState.contentTransformations }
      val canvasSizeChanges = snapshotFlow { state.canvasSize }.filter { it.isSpecified }

      canvasSizeChanges.flatMapLatest { canvasSize ->
        val tileGrid: BitmapTileGrid = generateBitmapTileGrid(
          canvasSize = canvasSize,
          unscaledImageSize = decoder.imageSize
        )

        transformations.map { transformation ->
          val sampleSize = BitmapSampleSize.calculateFor(
            canvasSize = canvasSize * transformation.scale,  // todo: this calculation doesn't look right.
            scaledImageSize = decoder.imageSize
          )
          val tiles = checkNotNull(tileGrid[sampleSize]) {
            "No tiles found for $sampleSize among ${tileGrid.keys}. This is unexpected. " +
              "Please file an issue on https://github.com/saket/telephoto?"
          }

          val scale = ScaleFactor(
            scaleX = transformation.scale * (state.canvasSize.width / decoder.imageSize.width),
            scaleY = transformation.scale * (state.canvasSize.height / decoder.imageSize.height)
          )
          tiles.fastMap {
            val visualBounds = it.bounds.copy(
              left = (it.bounds.left * scale.scaleX) + transformation.offset.x,
              right = (it.bounds.right * scale.scaleX) + transformation.offset.x,
              top = (it.bounds.top * scale.scaleY) + transformation.offset.y,
              bottom = (it.bounds.bottom * scale.scaleY) + transformation.offset.y,
            )
            it.copy(
              bounds = visualBounds,
              isVisible = visualBounds.overlaps(Offset.Zero, transformation.viewportSize)
            )
          }
        }
      }
        .flowOn(Dispatchers.IO)
        .onStart { emit(emptyList()) }  // Reset for new images (i.e., when this LaunchedEffect runs again).
        .collect { tiles ->
          state.visibleTiles = tiles
        }
    }
  }

  return state
}

@Stable
class SubSamplingImageState internal constructor() {
  internal var visibleTiles by mutableStateOf(emptyList<BitmapTile>())
  internal var canvasSize by mutableStateOf(Size.Unspecified)
}

private operator fun IntSize.times(other: Float): Size =
  Size(width = width * other, height = height * other)
