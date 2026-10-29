package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.util.fastForEach
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import me.saket.telephoto.subsamplingimage.internal.ImageCache.LoadingState.Failed
import me.saket.telephoto.subsamplingimage.internal.ImageCache.LoadingState.InFlight
import me.saket.telephoto.subsamplingimage.internal.ImageCache.LoadingState.Loaded
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class ImageCache(
  scope: CoroutineScope,
  private val decoder: ImageRegionDecoder,
  private val throttleEvery: Duration = 100.milliseconds,
  private val onDecodeFailed: (IOException) -> Unit = {},
) {
  private val visibleRegions = Channel<List<ImageRegionTile>>(capacity = 10)
  private val cachedImages = MutableStateFlow(emptyMap<ImageRegionTile, LoadingState>())
  private val isDecodeFailureReported = AtomicBoolean(false)

  private sealed interface LoadingState {
    data class Loaded(val painter: ImageRegionDecoder.DecodeResult) : LoadingState
    data class InFlight(val job: Job) : LoadingState

    /**
     * Decoding of a tile failed. The tile is kept in the cache so that it isn't retried for
     * as long as it stays visible. It will be unloaded like any other tile when it goes out
     * of the viewport and retried if it becomes visible again.
     */
    data object Failed : LoadingState
  }

  init {
    scope.launch {
      visibleRegions.consumeAsFlow()
        .distinctUntilChanged()
        .throttleLatest(throttleEvery)  // In case the image is animating its zoom.
        .collect { tiles ->
          val tilesToLoad = tiles.fastFilter { it !in cachedImages.value }
          tilesToLoad.fastForEach { tile ->
            // CoroutineStart.UNDISPATCHED is used to ensure that the coroutines are executed
            // in the same order they were launched. Otherwise, the tiles may load in a different
            // order than what was requested. SubSamplingImageTest#draw_tile_under_centroid_first()
            // test will also become flaky.
            launch(start = CoroutineStart.UNDISPATCHED) {
              cachedImages.update {
                check(tile !in it)
                it + (tile to InFlight(currentCoroutineContext().job))
              }
              val result = try {
                Loaded(decoder.decodeRegion(tile.bounds, tile.sampleSize.size))
              } catch (e: IOException) {
                // Make sure that a decoder's I/O error isn't mistaken for a failure
                // if this job was cancelled while the region was being decoded.
                currentCoroutineContext().ensureActive()
                // Only the first failure is reported. A broken image will fail for all of
                // its tiles, and the reporter's callback is meant to be per image, not per tile.
                if (isDecodeFailureReported.compareAndSet(false, true)) {
                  onDecodeFailed(e)
                }
                Failed
              }
              cachedImages.update {
                it + (tile to result)
              }
            }
          }

          val tilesToUnload = cachedImages.value.keys.filter { it !in tiles }
          tilesToUnload.fastForEach { region ->
            val inFlight = cachedImages.value[region] as? InFlight
            inFlight?.job?.cancel()
          }
          cachedImages.update { it - tilesToUnload.toSet() }
        }
    }
  }

  fun observeCachedImages(): Flow<ImmutableMap<ImageRegionTile, ImageRegionDecoder.DecodeResult>> {
    return cachedImages.map { map ->
      buildMap(capacity = map.size) {
        map.forEach { (region, state) ->
          if (state is Loaded) {
            put(region, state.painter)
          }
        }
      }.toImmutableMap()
    }.distinctUntilChanged()
  }

  fun loadOrUnloadForTiles(regions: List<ImageRegionTile>) {
    visibleRegions.trySend(regions)
  }

  // Copied from https://github.com/Kotlin/kotlinx.coroutines/issues/1446#issuecomment-1198103541.
  private fun <T> Flow<T>.throttleLatest(delay: Duration): Flow<T> {
    return conflate().transform {
      emit(it)
      delay(delay)
    }
  }
}
