@file:Suppress("NAME_SHADOWING")
@file:OptIn(ExperimentalCoroutinesApi::class)

package me.saket.telephoto.subsamplingimage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.saket.telephoto.subsamplingimage.internal.BitmapLoader
import me.saket.telephoto.subsamplingimage.internal.BitmapRegionTileGrid
import me.saket.telephoto.subsamplingimage.internal.BitmapSampleSize
import me.saket.telephoto.subsamplingimage.internal.CanvasRegionTile
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.SkiaImageRegionDecoders
import me.saket.telephoto.subsamplingimage.internal.calculateFor
import me.saket.telephoto.subsamplingimage.internal.fastMapNotNull
import me.saket.telephoto.subsamplingimage.internal.generate
import me.saket.telephoto.subsamplingimage.internal.maxScale
import me.saket.telephoto.subsamplingimage.internal.scaledAndOffsetBy
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomableContentTransformation
import me.saket.telephoto.zoomable.ZoomableViewportState
import java.io.IOException

// todo: doc.
// todo: move this to its own module.
@Composable
fun rememberSubSamplingImageState(
  viewportState: ZoomableViewportState,
  imageSource: ImageSource,
  eventListener: SubSamplingImageEventListener = SubSamplingImageEventListener.Empty
): SubSamplingImageState {

  val viewportEventListener = remember(eventListener) {
    object : SubSamplingImageEventListener by eventListener {
      override fun onImageLoaded(imageSize: Size) {
        eventListener.onImageLoaded(imageSize)

        // Assuming that there is no padding between this composable
        // and its viewport, the content location is reported as 0,0
        // because SubSamplingImage draws its content from top-start.
        val imageBoundsInParent = Rect(Offset.Zero, imageSize)
        viewportState.setContentLocation(object : ZoomableContentLocation {
          override fun boundsIn(parent: Rect): Rect = imageBoundsInParent
        })
      }
    }
  }

  return rememberSubSamplingImageState(
    imageSource = imageSource,
    transformation = viewportState.contentTransformation,
    eventListener = viewportEventListener,
  )
}

// todo: doc.
@Composable
fun rememberSubSamplingImageState(
  imageSource: ImageSource,
  transformation: ZoomableContentTransformation,
  eventListener: SubSamplingImageEventListener = SubSamplingImageEventListener.Empty
): SubSamplingImageState {
  val eventListener by rememberUpdatedState(eventListener)
  val transformation by rememberUpdatedState(transformation)

  val decoder: ImageRegionDecoder? by createRegionDecoder(imageSource, eventListener)

  val state = remember {
    SubSamplingImageState()
  }.also {
    it.eventListener = eventListener
  }

  LaunchedEffect(state, decoder) {
    // Reset tiles for new images.
    state.tiles = emptyList()
  }

  decoder?.let { decoder ->
    state.imageSize = decoder.imageSize
    val transformations = remember { snapshotFlow { transformation } }

    val scope = rememberCoroutineScope()
    LaunchedEffect(state, transformations, decoder) {
      val bitmapLoader = BitmapLoader(decoder, scope)
      val canvasSizeChanges = snapshotFlow { state.canvasSize }
        .filter { it.isSpecified }
        .filter { it.minDimension > 0f }

      canvasSizeChanges.flatMapLatest { canvasSize ->
        val tileGrid = BitmapRegionTileGrid.generate(
          canvasSize = canvasSize,
          unscaledImageSize = decoder.imageSize
        )

        val viewportBounds = transformations
          .map { it.viewportSize }
          .distinctUntilChanged()
          .map { size -> Rect(Offset.Zero, size) }

        combine(
          transformations,
          viewportBounds,
          bitmapLoader.cachedBitmaps()
        ) { transformation, viewportBounds, bitmaps ->
          val sampleSize = BitmapSampleSize.calculateFor(transformation.scale.maxScale)
          val foregroundRegions = tileGrid.foreground[sampleSize].orEmpty()

          val foregroundTiles = foregroundRegions.fastMapNotNull { tile ->
            val drawBounds = tile.bounds.scaledAndOffsetBy(transformation.scale, transformation.offset)
            if (drawBounds.overlaps(viewportBounds)) {
              CanvasRegionTile(
                bounds = drawBounds,
                bitmap = bitmaps[tile],
                bitmapRegion = tile
              )
            } else {
              null
            }
          }

          // Fill any missing gaps in tiles by drawing the low-res base tile underneath as
          // a fallback. The base tile will hide again when all bitmaps have been loaded.
          val canDrawBaseTile = foregroundTiles.isEmpty() || foregroundTiles.fastAny { it.bitmap == null }

          // The base tile needs to be always present even if it isn't going to
          // be drawn. Otherwise BitmapLoader will remove its bitmap from cache.
          val baseTile = if (canDrawBaseTile) {
            tileGrid.base.let { tile ->
              CanvasRegionTile(
                bounds = tile.bounds.scaledAndOffsetBy(transformation.scale, transformation.offset),
                bitmap = bitmaps[tile],
                bitmapRegion = tile,
              )
            }
          } else null

          // Side effect, ew :(.
          bitmapLoader.loadOrUnloadForTiles(listOf(tileGrid.base) + foregroundRegions)

          return@combine (listOfNotNull(baseTile) + foregroundTiles)
        }
      }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
        .collect { tiles ->
          state.tiles = tiles
        }
    }
  }

  return state
}

@Composable
private fun createRegionDecoder(
  imageSource: ImageSource,
  eventListener: SubSamplingImageEventListener
): State<ImageRegionDecoder?> {
  val context = LocalContext.current
  val eventListener by rememberUpdatedState(eventListener)

  val decoder = remember { mutableStateOf<ImageRegionDecoder?>(null) }
  val isInPreviewMode = LocalInspectionMode.current

  if (!isInPreviewMode) {
    LaunchedEffect(imageSource) {
      try {
        decoder.value = SkiaImageRegionDecoders.create(context, imageSource).also {
          eventListener.onImageLoaded(it.imageSize)
        }
      } catch (e: IOException) {
        eventListener.onImageLoadingFailed(e)
      }
    }
  }

  return decoder
}

// todo: doc.
@Stable
class SubSamplingImageState internal constructor() {
  internal var tiles by mutableStateOf(emptyList<CanvasRegionTile>())
  internal var canvasSize by mutableStateOf(Size.Unspecified)
  internal var imageSize by mutableStateOf(Size.Unspecified)

  internal lateinit var eventListener: SubSamplingImageEventListener
  private var firstDrawEventSent = false

  internal fun maybeSendFirstDrawEvent() {
    if (!firstDrawEventSent
      && canvasSize.minDimension > 0f // Wait until content size is measured in case of wrap_content.
      && tiles.isNotEmpty() && tiles.all { it.bitmap != null }
    ) {
      eventListener.onImageDisplayed()
      firstDrawEventSent = true
    }
  }

  companion object {
    // Only used by tests.
    internal var showTileBounds = false
  }
}
