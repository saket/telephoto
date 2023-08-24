package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.util.fastForEach
import com.hoc081098.flowext.throttleTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.saket.telephoto.subsamplingimage.internal.BitmapLoader.LoadingState.InFlight
import me.saket.telephoto.subsamplingimage.internal.BitmapLoader.LoadingState.Loaded
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class BitmapLoader(
  private val decoder: ImageRegionDecoder,
  private val scope: CoroutineScope,
) {
  private val cachedBitmaps = MutableStateFlow(emptyMap<BitmapRegionTile, LoadingState>())
  private val loadRequests = Channel<BitmapRegionTile>()

  private sealed interface LoadingState {
    data class Loaded(val bitmap: ImageBitmap) : LoadingState
    data class InFlight(val job: Job) : LoadingState
  }

  init {
    scope.launch {
      loadRequests.consumeAsFlow()
        // Throttling is useful in case the image is animating its zoom.
        .throttleTime { 100.milliseconds }
        .collect { region ->
          val job = launch {
            val bitmap = decoder.decodeRegion(region)
            cachedBitmaps.update { it + (region to Loaded(bitmap)) }
          }
          cachedBitmaps.update { it + (region to InFlight(job)) }
        }
    }
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

    regionsToLoad.fastForEach { region ->
      loadRequests.trySend(region)
    }

    regionsToUnload.fastForEach { region ->
      val inFlight = cachedBitmaps.value[region] as? InFlight
      inFlight?.job?.cancel()
    }
    cachedBitmaps.update { it - regionsToUnload.toSet() }
  }
}
