package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.saket.telephoto.subsamplingimage.internal.BitmapLoader.LoadingState.InFlight
import me.saket.telephoto.subsamplingimage.internal.BitmapLoader.LoadingState.Loaded

internal class BitmapLoader(
  private val decoder: ImageRegionDecoder,
  private val scope: CoroutineScope,
) {
  private val bitmaps = MutableStateFlow(emptyMap<BitmapRegionBounds, LoadingState>())

  private sealed interface LoadingState {
    data class Loaded(val bitmap: ImageBitmap) : LoadingState
    data class InFlight(val job: Job) : LoadingState
  }

  fun bitmaps(): Flow<Map<BitmapRegionBounds, ImageBitmap>> {
    return bitmaps.map { map ->
      buildMap(capacity = map.size) {
        map.forEach { (region, state) ->
          if (state is Loaded) {
            put(region, state.bitmap)
          }
        }
      }
    }.distinctUntilChanged()
  }

  fun loadOrUnloadForTiles(tiles: List<BitmapTile>) {
    tiles.fastForEach { tile ->
      val existing = bitmaps.value[tile.regionBounds]

      if (tile.isVisible && existing == null) {
        // Tile just became visible.
        val job = scope.launch {
          val bitmap = decoder.decodeRegion(tile.regionBounds, tile.sampleSize)
          bitmaps.update { it + (tile.regionBounds to Loaded(bitmap)) }
        }
        bitmaps.update { it + (tile.regionBounds to InFlight(job)) }
      }

      if (!tile.isVisible && existing != null) {
        // Tile was visible before, but has now gone out of viewport bounds.
        if (existing is InFlight) {
          existing.job.cancel()
        }
        bitmaps.update { it - tile.regionBounds }
      }
    }

    // Remove stale bitmaps whose tiles are no longer visible.
    val currentTileBounds = tiles.map { it.regionBounds }
    val itemsToRemove = bitmaps.value.filterKeys { it !in currentTileBounds }
    itemsToRemove.forEach { (_, state) ->
      if (state is InFlight) {
        state.job.cancel()
      }
    }
    bitmaps.update { it - itemsToRemove.keys }
  }
}
