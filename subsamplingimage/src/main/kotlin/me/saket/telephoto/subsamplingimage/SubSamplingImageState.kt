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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.saket.telephoto.subsamplingimage.internal.BitmapLoader
import me.saket.telephoto.subsamplingimage.internal.BitmapRegionTileGrid
import me.saket.telephoto.subsamplingimage.internal.BitmapSampleSize
import me.saket.telephoto.subsamplingimage.internal.CanvasRegionTile
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.LocalImageRegionDecoderFactory
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
// todo: should this accept a ZoomableContentTransformationProvider?
//  - the provider can have a setContentLocation function and a viewportSize field.
@Composable
fun rememberSubSamplingImageState(
  imageSource: ImageSource,
  viewportState: ZoomableViewportState,
  errorReporter: SubSamplingImageErrorReporter = SubSamplingImageErrorReporter.NoOpInRelease
): SubSamplingImageState {
  val state = rememberSubSamplingImageState(
    imageSource = imageSource,
    errorReporter = errorReporter,
    transformation = viewportState.contentTransformation,
  )

  LaunchedEffect(state.imageSize) {
    val contentLocation = state.imageSize?.let { imageSize ->
      // Assuming that there is no padding between this composable
      // and its viewport, the content location is reported as 0,0
      // because SubSamplingImage draws its content from top-start.
      val imageBoundsInParent = Rect(Offset.Zero, imageSize)
      object : ZoomableContentLocation {
        override fun boundsIn(parent: Rect, direction: LayoutDirection): Rect = imageBoundsInParent
      }
    }
    viewportState.setContentLocation(contentLocation ?: ZoomableContentLocation.Unspecified)
  }

  return state
}

// todo: doc.
@Composable
fun rememberSubSamplingImageState(
  imageSource: ImageSource,
  transformation: ZoomableContentTransformation,
  errorReporter: SubSamplingImageErrorReporter = SubSamplingImageErrorReporter.NoOpInRelease
): SubSamplingImageState {
  val errorReporter by rememberUpdatedState(errorReporter)
  val transformation by rememberUpdatedState(transformation)
  val decoder: ImageRegionDecoder? by createRegionDecoder(imageSource, errorReporter)

  val state = remember {
    SubSamplingImageState()
  }

  // Reset everything when a new image is set.
  LaunchedEffect(state, decoder) {
    state.imageSize = decoder?.imageSize
    state.isImageDisplayed = false
    state.tiles = emptyList()
  }

  decoder?.let { decoder ->
    val transformations = remember { snapshotFlow { transformation } }

    val scope = rememberCoroutineScope()
    LaunchedEffect(state, transformations, decoder) {
      val bitmapLoader = BitmapLoader(decoder, scope)
      val canvasSizeChanges = snapshotFlow { state.canvasSize }
        .filter { it.isSpecified }
        .filter { it.minDimension > 0f }

      canvasSizeChanges.flatMapLatest { canvasSize ->
        val tileGrids = transformations.distinctUntilChangedBy { it.viewportBounds }.map {
          BitmapRegionTileGrid.generate(
            canvasSize = canvasSize,
            unscaledImageSize = decoder.imageSize,
            minTileSize = it.viewportBounds.size / 2f,
          )
        }

        combine(
          tileGrids,
          transformations,
          bitmapLoader.cachedBitmaps()
        ) { tileGrid, transformation, bitmaps ->
          val sampleSize = BitmapSampleSize.calculateFor(transformation.scale.maxScale)
          val foregroundRegions = tileGrid.foreground[sampleSize].orEmpty()

          val foregroundTiles = foregroundRegions.fastMapNotNull { tile ->
            val drawBounds = tile.bounds.scaledAndOffsetBy(transformation.scale, transformation.offset)
            if (drawBounds.overlaps(transformation.viewportBounds)) {
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
          bitmapLoader.loadOrUnloadForTiles(listOf(tileGrid.base) + foregroundTiles.map { it.bitmapRegion })

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
  errorReporter: SubSamplingImageErrorReporter
): State<ImageRegionDecoder?> {
  val context = LocalContext.current
  val errorReporter by rememberUpdatedState(errorReporter)

  val decoder = remember(imageSource) { mutableStateOf<ImageRegionDecoder?>(null) }
  val isInPreviewMode = LocalInspectionMode.current

  if (!isInPreviewMode) {
    val factory = LocalImageRegionDecoderFactory.current
    LaunchedEffect(imageSource) {
      try {
        decoder.value = factory.create(context, imageSource)
      } catch (e: IOException) {
        errorReporter.onImageLoadingFailed(e, imageSource)
      }
    }
  }

  return decoder
}

// todo: doc.
@Stable
class SubSamplingImageState internal constructor() {
  var imageSize: Size? by mutableStateOf(null)
  var isImageDisplayed: Boolean by mutableStateOf(false)

  internal var tiles by mutableStateOf(emptyList<CanvasRegionTile>())
  internal var canvasSize by mutableStateOf(Size.Unspecified)
  internal var showTileBounds = false  // Only used by tests.

  internal fun maybeSendFirstDrawEvent() {
    if (!isImageDisplayed
      && canvasSize.minDimension > 0f // Wait until content size is measured in case of wrap_content.
      && tiles.isNotEmpty() && tiles.all { it.bitmap != null }
    ) {
      isImageDisplayed = true
    }
  }
}
