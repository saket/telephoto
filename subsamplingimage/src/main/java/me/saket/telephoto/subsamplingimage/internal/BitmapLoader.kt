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
  private val cachedBitmaps = MutableStateFlow(emptyMap<BitmapRegionTile, LoadingState>())

  private sealed interface LoadingState {
    data class Loaded(val bitmap: ImageBitmap) : LoadingState
    data class InFlight(val job: Job) : LoadingState
  }

  fun cachedBitmaps(): Flow<Map<BitmapRegionTile, ImageBitmap>> {
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

  fun loadOrUnloadForTiles(tiles: List<BitmapRegionTile>) {
    val regionsToLoad = tiles
      .filter { it !in cachedBitmaps.value }

    val regionsToUnload = cachedBitmaps.value.keys
      .filter { it !in tiles }
      .toSet()

    regionsToLoad.forEach { region ->
      val job = scope.launch {
        val bitmap = decoder.decodeRegion(region)
        cachedBitmaps.update { it + (region to Loaded(bitmap)) }
      }
      cachedBitmaps.update { it + (region to InFlight(job)) }
    }

    regionsToUnload.forEach { region ->
      val inFlight = cachedBitmaps.value[region] as? InFlight
      inFlight?.job?.cancel()
    }
    cachedBitmaps.update { it - regionsToUnload }
  }
}
