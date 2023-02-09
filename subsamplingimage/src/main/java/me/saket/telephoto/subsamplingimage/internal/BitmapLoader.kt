package me.saket.telephoto.subsamplingimage.internal

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class BitmapLoader(
  private val decoder: SkiaImageRegionDecoder,
  private val scope: CoroutineScope,
) {
  val bitmaps = MutableStateFlow(emptyMap<BitmapRegionBounds, Bitmap>())
  private val ongoingDecodes = MutableStateFlow(emptyMap<BitmapRegionBounds, Job>())

  fun loadOrUnloadTiles(tiles: List<BitmapTile>) {
    tiles.fastForEach { tile ->
      if (tile.isVisible) {
        if (tile.regionBounds !in bitmaps.value && tile.regionBounds !in ongoingDecodes.value) {
          val decodeJob = scope.launch {
//            println("Loading bitmap for tile [${tile.regionBounds}]")
            val bitmap = decoder.decodeRegion(tile.regionBounds, tile.sampleSize)
            bitmaps.update { existing -> existing + (tile.regionBounds to bitmap) }
            ongoingDecodes.update { existing -> existing - tile.regionBounds }
          }
          ongoingDecodes.update { existing -> existing + (tile.regionBounds to decodeJob) }
        }

      } else {
        if (tile.regionBounds in bitmaps.value) {
//          println("Removing bitmap for tile [${tile.regionBounds}]")
          bitmaps.update { existing ->
            existing - tile.regionBounds
          }
        }

        val ongoingDecode = ongoingDecodes.value[tile.regionBounds]
        if (ongoingDecode != null) {
//          println("Canceling bitmap load for tile [${tile.regionBounds}]")
          ongoingDecode.cancel()
          ongoingDecodes.update { it - tile.regionBounds }
        }
      }
    }

    bitmaps.update { existing ->
      existing - (
        existing.keys - tiles.map { it.regionBounds }.toSet()
      )
    }
  }
}

@JvmInline
internal value class BitmapRegionBounds(
  val bounds: Rect,
) {
  override fun toString(): String {
    return "Rect(${bounds.topLeft}, ${bounds.size})"
  }
}
