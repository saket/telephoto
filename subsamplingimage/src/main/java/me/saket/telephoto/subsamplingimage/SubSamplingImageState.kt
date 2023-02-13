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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.saket.telephoto.subsamplingimage.internal.BitmapLoader
import me.saket.telephoto.subsamplingimage.internal.BitmapSampleSize
import me.saket.telephoto.subsamplingimage.internal.BitmapTile
import me.saket.telephoto.subsamplingimage.internal.BitmapTileGrid
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.SkiaImageRegionDecoders
import me.saket.telephoto.subsamplingimage.internal.calculateFor
import me.saket.telephoto.subsamplingimage.internal.generateBitmapTileGrid
import me.saket.telephoto.zoomable.ZoomableState
import java.io.IOException

@Composable
fun rememberSubSamplingImageState(
  viewportState: ZoomableState,
  imageSource: ImageSource,
  eventListener: SubSamplingImageEventListener = SubSamplingImageEventListener.Empty
): SubSamplingImageState {
  val context = LocalContext.current
  val eventListener by rememberUpdatedState(eventListener)

  val viewportEventListener = remember(eventListener) {
    object : SubSamplingImageEventListener by eventListener {
      override fun onImageLoaded(imageSize: Size) {
        eventListener.onImageLoaded(imageSize)
        viewportState.setUnscaledContentSize(imageSize)
      }

      override fun onImageDisplayed() {
        eventListener.onImageDisplayed()
      }
    }
  }

  val decoder by produceState<ImageRegionDecoder?>(initialValue = null, key1 = imageSource) {
    try {
      value = SkiaImageRegionDecoders.create(context, imageSource).also {
        viewportEventListener.onImageLoaded(it.imageSize)
      }
    } catch (e: IOException) {
      viewportEventListener.onImageLoadingFailed(e)
    }
  }

  val state = remember {
    SubSamplingImageState()
  }.also {
    it.eventListener = viewportEventListener
  }

  LaunchedEffect(state, decoder) {
    // Reset tiles for new images.
    state.tiles = emptyList()
  }

  decoder?.let { decoder ->
    state.imageSize = decoder.imageSize

    val scope = rememberCoroutineScope()
    LaunchedEffect(state, viewportState, decoder) {
      val bitmapLoader = BitmapLoader(decoder, scope)
      val transformations = snapshotFlow { viewportState.contentTransformations }
      val canvasSizeChanges = snapshotFlow { state.canvasSize }
        .filter { it.isSpecified }
        .filter { it.minDimension > 0f }

      canvasSizeChanges.flatMapLatest { canvasSize ->
        val tileGrid: BitmapTileGrid = generateBitmapTileGrid(
          canvasSize = canvasSize,
          unscaledImageSize = decoder.imageSize
        )

        val inflatedViewportBounds = transformations
          .map { it.viewportSize }
          .distinctUntilChanged()
          .map { size ->
            // TODO: inflating the bounds results in a higher number of bitmaps in memory.
            //  Test if this is worth keeping.
            Rect(Offset.Zero, size)/*.inflateByPercent(0.1f)*/
          }

        combine(
          transformations,
          inflatedViewportBounds,
          bitmapLoader.cachedBitmaps()
        ) { transformation, viewportBounds, bitmaps ->
          val zoom = transformation.scale * minOf(
            canvasSize.width / decoder.imageSize.width,
            canvasSize.height / decoder.imageSize.height
          )

          val sampleSize = BitmapSampleSize.calculateFor(zoom)
          val tiles = checkNotNull(tileGrid[sampleSize]) {
            "No tiles found for $sampleSize among ${tileGrid.keys}"
          }

          tiles.fastMap { tile ->
            val drawBounds = tile.regionBounds.bounds.let {
              it.copy(
                left = (it.left * zoom) + transformation.offset.x,
                right = (it.right * zoom) + transformation.offset.x,
                top = (it.top * zoom) + transformation.offset.y,
                bottom = (it.bottom * zoom) + transformation.offset.y,
              )
            }
            val isVisible = drawBounds.overlaps(viewportBounds)
            tile.copy(
              drawBounds = drawBounds,
              isVisible = isVisible,
              bitmap = bitmaps[tile.regionBounds]
            )
          }
        }
      }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
        .collect { tiles ->
          bitmapLoader.loadOrUnloadForTiles(tiles)
          state.tiles = tiles
        }
    }
  }

  return state
}

@Stable
class SubSamplingImageState internal constructor() {
  internal var tiles by mutableStateOf(emptyList<BitmapTile>())
  internal var canvasSize by mutableStateOf(Size.Unspecified)
  internal var imageSize by mutableStateOf(Size.Unspecified)

  internal lateinit var eventListener: SubSamplingImageEventListener
  private var firstDrawEventSent = false

  fun maybeSendFirstDrawEvent() {
    if (!firstDrawEventSent && tiles.isNotEmpty() && tiles.all { it.isVisible && it.bitmap != null }) {
      eventListener.onImageDisplayed()
      firstDrawEventSent = true
    }
  }

  companion object {
    // Only used by tests.
    internal var showTileBounds = true
  }
}

internal fun Rect.inflateByPercent(percent: Float): Rect {
  return Rect(
    left = left - (width * percent),
    top = top - (height * percent),
    right = right + (width * percent),
    bottom = bottom + (height * percent)
  )
}
