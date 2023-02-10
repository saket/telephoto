package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.graphics.ImageBitmap
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
  private val cachedBitmaps = MutableStateFlow(emptyMap<BitmapRegionBounds, LoadingState>())

  private sealed interface LoadingState {
    data class Loaded(val bitmap: ImageBitmap) : LoadingState
    data class InFlight(val job: Job) : LoadingState
  }

  fun cachedBitmaps(): Flow<Map<BitmapRegionBounds, ImageBitmap>> {
    return cachedBitmaps.map { map ->
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
    val tilesToLoad = tiles
      .filter { it.isVisible }
      .filter { it.regionBounds !in cachedBitmaps.value }

    val regionsToUnload = run {
      val invisibleTileRegions = tiles
        .filterNot { it.isVisible }
        .map { it.regionBounds }
        .toSet()

      val removedTileRegions = run {
        val currentRegions = tiles.map { it.regionBounds }
        cachedBitmaps.value.keys
          .filter { it !in currentRegions }
          .toSet()
      }
      return@run invisibleTileRegions + removedTileRegions
    }

    tilesToLoad.forEach { tile ->
      val job = scope.launch {
        val bitmap = decoder.decodeRegion(tile.regionBounds, tile.sampleSize)
        cachedBitmaps.update { it + (tile.regionBounds to Loaded(bitmap)) }
      }
      cachedBitmaps.update { it + (tile.regionBounds to InFlight(job)) }
    }

    regionsToUnload.forEach { region ->
      val inFlight = cachedBitmaps.value[region] as? InFlight
      inFlight?.job?.cancel()
    }
    cachedBitmaps.update { it - regionsToUnload }
  }
}
