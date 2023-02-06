@file:Suppress("NAME_SHADOWING")

package me.saket.telephoto.subsamplingimage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import me.saket.telephoto.subsamplingimage.internal.BitmapSampleSize
import me.saket.telephoto.subsamplingimage.internal.BitmapTile
import me.saket.telephoto.subsamplingimage.internal.SkiaImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.calculateFor
import me.saket.telephoto.subsamplingimage.internal.generateBitmapTileGrid
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
    LaunchedEffect(zoomableState, decoder) {
      val transformations = snapshotFlow { zoomableState.contentTransformations }
      val canvasSizeChanges = snapshotFlow { state.canvasSize }

      val tileGrids = canvasSizeChanges
        .flowOn(Dispatchers.IO)
        .map { canvasSize ->
          generateBitmapTileGrid(
            canvasSize = canvasSize,
            unscaledImageSize = decoder.imageSize
          )
        }

      combine(tileGrids, transformations, canvasSizeChanges) { tileGrid, transformation, canvasSize ->
        val sampleSize = BitmapSampleSize.calculateFor(
          canvasSize = canvasSize * transformation.scale,  // todo: this calculation doesn't look right.
          scaledImageSize = decoder.imageSize
        )
        checkNotNull(tileGrid[sampleSize]) {
          "No tiles found for $sampleSize. This is unexpected. " +
            "Please file an issue on https://github.com/saket/telephoto?"
        }
      }
        .onStart { emit(emptyList()) }  // Reset for new images.
        .collect { tiles ->
          state.visibleTiles.clear()
          state.visibleTiles.addAll(tiles)
        }
    }

    LaunchedEffect(state, zoomableState.contentTransformations, state.canvasSize) {
      val transformation = zoomableState.contentTransformations
      state.scale = ScaleFactor(
        scaleX = transformation.scale * (state.canvasSize.width / decoder.imageSize.width),
        scaleY = transformation.scale * (state.canvasSize.height / decoder.imageSize.height)
      )
      state.translation = transformation.offset
    }
  }

  return state
}

@Stable
class SubSamplingImageState internal constructor() {
  internal val visibleTiles = mutableStateListOf<BitmapTile>()
  internal var canvasSize by mutableStateOf(Size.Unspecified)

  internal var scale by mutableStateOf(ScaleFactor(1f, 1f))
  internal var translation by mutableStateOf(Offset.Zero)
}

private operator fun IntSize.times(other: Float): Size =
  Size(width = width * other, height = height * other)
