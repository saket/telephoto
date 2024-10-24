package me.saket.telephoto.subsamplingimage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.launch
import me.saket.telephoto.subsamplingimage.internal.BitmapSampleSize
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.ImageRegionTile
import me.saket.telephoto.subsamplingimage.internal.ImageRegionTileGrid
import me.saket.telephoto.subsamplingimage.internal.RotatedBitmapPainter
import me.saket.telephoto.subsamplingimage.internal.ViewportTile
import me.saket.telephoto.subsamplingimage.internal.calculateFor
import me.saket.telephoto.subsamplingimage.internal.contains
import me.saket.telephoto.subsamplingimage.internal.fastFilter
import me.saket.telephoto.subsamplingimage.internal.fastMapNotNull
import me.saket.telephoto.subsamplingimage.internal.generate
import me.saket.telephoto.subsamplingimage.internal.isNotEmpty
import me.saket.telephoto.subsamplingimage.internal.maxScale
import me.saket.telephoto.subsamplingimage.internal.overlaps
import me.saket.telephoto.subsamplingimage.internal.scaledAndOffsetBy
import me.saket.telephoto.zoomable.ZoomableContentTransformation

/** State for [SubSamplingImage]. */
@Stable
internal class RealSubSamplingImageState(
  private val imageSource: SubSamplingImageSource
) : SubSamplingImageState {

  override var imageSize: IntSize? by mutableStateOf(null)

  /**
   * Whether the image is loaded and displayed (not necessarily in its full quality).
   *
   * Also see [isImageLoadedInFullQuality].
   */
  override val isImageLoaded: Boolean by derivedStateOf {
    (
      canvasSize != null && visibleViewportTiles.isNotEmpty() &&
      (loadedBitmaps.fastAny { it == tileGrid?.base } || visibleViewportTiles.fastAll { it.region in loadedBitmaps })
    ).also {
//        if (!it) {
//          println("visible tiles =")
//          visibleViewportTiles.fastForEach { println("* $it") }
//        }
      }
  }

  /** Whether the image is loaded and displayed in its full quality. */
  override val isImageLoadedInFullQuality: Boolean by derivedStateOf {
    isImageLoaded && viewportTiles.fastAll { it.region in loadedBitmaps }
  }

  internal var showTileBounds = false  // Only used by tests.

  internal var canvasSize: IntSize? by mutableStateOf(null)
  internal var contentTransformation: () -> ZoomableContentTransformation? by mutableStateOf({ null })

  val tileGrid: ImageRegionTileGrid? by derivedStateOf {
    val canvasSize = canvasSize
    val imageSize = imageSize
    if (canvasSize?.isNotEmpty() == true && imageSize?.isNotEmpty() == true) {
      ImageRegionTileGrid.generate(
        canvasSize = canvasSize,
        unscaledImageSize = imageSize,
      )
    } else null
  }

  private val foregroundRegions: List<ImageRegionTile> by derivedStateOf {
    val sampleSizeAtCurrentZoom = contentTransformation()?.let {
      BitmapSampleSize.calculateFor(zoom = it.scale.maxScale)
    }
    val tileGrid = tileGrid
    if (tileGrid != null && sampleSizeAtCurrentZoom != null) {
      tileGrid.foreground[sampleSizeAtCurrentZoom].orEmpty()
    } else {
      emptyList()
    }
  }

  val viewportTiles: List<ViewportTile> by derivedStateOf {
    val transformation = contentTransformation()
    val baseTile = tileGrid?.base

    if (transformation == null || baseTile == null) {
      emptyList()
    } else {
      // Fill any missing gaps in tiles by drawing the low-res base tile underneath as
      // a fallback. The base tile will hide again when all bitmaps have been loaded.
      val canDrawBaseTile = true //foregroundRegions.isEmpty() || foregroundRegions.fastAny { it !in loadedBitmaps }

      (listOf(baseTile) + foregroundRegions)
        .sortedByDescending { it.bounds.contains(transformation.centroid) }
        .fastMapNotNull { region ->
          val drawBounds = region.bounds.scaledAndOffsetBy(transformation.scale, transformation.offset)
          val isBaseTile = region == tileGrid?.base
          ViewportTile(
            region = region,
            bounds = drawBounds,
            isVisible = if (isBaseTile) canDrawBaseTile else drawBounds.overlaps(canvasSize!!),
          )
        }
    }
  }

  private val visibleViewportTiles: List<ViewportTile> get() = viewportTiles.fastFilter { it.isVisible }

  // todo: convert to a set?
  private val loadedBitmaps = mutableStateListOf<ImageRegionTile>()

  // todo: why is this not a constructor param?
  var imageRegionDecoder: ImageRegionDecoder? by mutableStateOf(null)

  // todo: throttle fast decodes
  @Composable
  fun loadImage(region: ImageRegionTile): Painter? {
    var painter: Painter? by remember(region) {
      mutableStateOf(
        (if (region == tileGrid?.base) imageSource.preview else null)?.let(::BitmapPainter)
      )
    }

    // Note to self: I'm not using produceValue() because it'll
    // not invalidate the cached bitmap even if its key changes.
    val scope = rememberCoroutineScope()
    DisposableEffect(region) {
      scope.launch {
        painter = RotatedBitmapPainter(imageRegionDecoder!!.decodeRegion(region))
        loadedBitmaps.add(region)
      }
      onDispose {
        loadedBitmaps.remove(region)
      }
    }
    return painter
  }
}
