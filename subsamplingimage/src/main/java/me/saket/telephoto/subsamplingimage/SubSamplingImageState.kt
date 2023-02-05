@file:Suppress("NAME_SHADOWING")

package me.saket.telephoto.subsamplingimage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.saket.telephoto.subsamplingimage.internal.SkiaImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.BitmapSampleSize
import me.saket.telephoto.subsamplingimage.internal.BitmapTile
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

  LaunchedEffect(zoomableState, decoder) {
    val decoder = snapshotFlow { decoder }.filterNotNull().first()
    val transformations = snapshotFlow { zoomableState.contentTransformations }

    transformations
      .map { it.viewportSize }
      .distinctUntilChanged()
      .collect { viewportSize ->
        val baseSampleSize = BitmapSampleSize.calculateFor(
          viewportSize = viewportSize,
          scaledImageSize = decoder.imageSize.toSize()
        )
        val tileGridPerSampleLevel = mutableMapOf<BitmapSampleSize, List<BitmapTile>>()

        var sampleSize = baseSampleSize
        do {
          val tileCountOnEachAxis = (baseSampleSize / sampleSize).size
          val tileSize = decoder.imageSize / tileCountOnEachAxis

          val tileGrid = ArrayList<BitmapTile>(tileCountOnEachAxis * tileCountOnEachAxis)
          for (x in 0 until tileCountOnEachAxis) {
            for (y in 0 until tileCountOnEachAxis) {
              val tile = BitmapTile(
                bitmap = null,
                isLoadingBitmap = false,
                sampleSize = sampleSize,
                bounds = IntRect(
                  offset = IntOffset(
                    x = x * tileSize.width,
                    y = y * tileSize.height
                  ),
                  size = tileSize
                )
              )
              tileGrid.add(tile)
            }
          }
          tileGridPerSampleLevel[sampleSize] = tileGrid

          sampleSize /= 2
        } while (sampleSize.size >= 1)

        println("---------------------------------------")
        println("Generated tiles: ")
        tileGridPerSampleLevel.forEach { (sample, grid) ->
          println("[${sample.size}] = ${grid.size} tiles")
        }
      }
  }

  val zoomableState = rememberUpdatedState(zoomableState)
  return remember(decoder) {
    SubSamplingImageState(decoder)
  }
}

@Stable
class SubSamplingImageState internal constructor(
  private val decoder: SkiaImageRegionDecoder?,
) {
  internal val tiles = mutableStateMapOf<BitmapSampleSize, List<BitmapTile>>()
}

private operator fun IntSize.times(other: Float): Size =
  Size(width = width * other, height = height * other)
