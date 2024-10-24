package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.job
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BitmapCacheTest {
  private val decoder = FakeImageRegionDecoder()

  private fun TestScope.bitmapCache(
    throttleEvery: Duration = 100.milliseconds
  ) = BitmapCache(
    scope = backgroundScope,
    decoder = decoder,
    throttleEvery = throttleEvery,
  )

  @Test fun `when tiles are received, load bitmaps only for new tiles`() = runTest(timeout = 1.seconds) {
    turbineScope {
      val cache = bitmapCache(2.seconds)
      val requestedRegions = decoder.requestedRegions.testIn(this)
      val cachedBitmaps = cache.cachedBitmaps().testIn(this)
      assertThat(cachedBitmaps.awaitItem()).isEmpty() // Default item.

      val tile1 = fakeBitmapRegionTile(4)
      val tile2 = fakeBitmapRegionTile(4)

      cache.loadOrUnloadForTiles(listOf(tile1, tile2))
      decoder.decodedBitmaps.send(FakeImageBitmap())
      decoder.decodedBitmaps.send(FakeImageBitmap())

      assertThat(requestedRegions.awaitItem()).isEqualTo(tile1)
      assertThat(requestedRegions.awaitItem()).isEqualTo(tile2)
      cachedBitmaps.skipItems(1)
      assertThat(cachedBitmaps.awaitItem().keys.toList()).containsExactly(tile1, tile2)

      val tile3 = fakeBitmapRegionTile(4)
      cache.loadOrUnloadForTiles(listOf(tile1, tile2, tile3))
      decoder.decodedBitmaps.send(FakeImageBitmap())

      assertThat(requestedRegions.awaitItem()).isEqualTo(tile3)
      assertThat(cachedBitmaps.awaitItem().keys.toList()).containsExactly(tile1, tile2, tile3)

      requestedRegions.cancelAndExpectNoEvents()
      cachedBitmaps.cancelAndExpectNoEvents()
    }
  }

  @Test fun `when tiles are removed, discard their stale bitmaps from cache`() = runTest(timeout = 1.seconds) {
    val cache = bitmapCache(2.seconds)

    cache.cachedBitmaps().drop(1).test {
      val tile1 = fakeBitmapRegionTile(4)
      val tile2 = fakeBitmapRegionTile(4)
      cache.loadOrUnloadForTiles(listOf(tile1, tile2))
      decoder.decodedBitmaps.send(FakeImageBitmap())
      decoder.decodedBitmaps.send(FakeImageBitmap())

      skipItems(1)
      assertThat(awaitItem().keys.toList()).containsExactly(tile1, tile2)

      val tile3 = fakeBitmapRegionTile(4)
      cache.loadOrUnloadForTiles(listOf(tile3))
      decoder.decodedBitmaps.send(FakeImageBitmap())

      skipItems(1)
      assertThat(awaitItem().keys.toList()).containsExactly(tile3)

      cancelAndExpectNoEvents()
    }
  }

  @Test fun `when a tile is removed before its bitmap could be loaded, cancel its in-flight load`() =
    runTest(timeout = 1.seconds) {
      turbineScope {
        val cache = bitmapCache(2.seconds)
        val requestedRegions = decoder.requestedRegions.testIn(this)
        val cachedBitmaps = cache.cachedBitmaps().drop(1).testIn(this)

        val visibleTile = fakeBitmapRegionTile(4)
        cache.loadOrUnloadForTiles(listOf(visibleTile))
        assertThat(requestedRegions.awaitItem()).isEqualTo(visibleTile)
        cachedBitmaps.expectNoEvents()

        cache.loadOrUnloadForTiles(emptyList())
        requestedRegions.cancelAndExpectNoEvents()
        cachedBitmaps.cancelAndExpectNoEvents()

        // Verify that BitmapCache has cancelled all loading jobs.
        // I don't think it's possible to uniquely identify BitmapCache's loading jobs.
        // Checking that there aren't any active jobs should be sufficient for now.
        assertThat(coroutineContext.job.children.none { it.isActive }).isTrue()
      }
    }

  // Note to self: I'm using runBlocking() instead of runTest() here so that I can test delays.
  // and also to work around https://github.com/cashapp/paparazzi/issues/1101.
  @Test fun `throttle load requests`() = runBlocking {
    val scope: CoroutineScope = this.plus(Job())
    val cache = BitmapCache(
      scope = scope,
      decoder = decoder,
      throttleEvery = 2.seconds,
    )

    decoder.requestedRegions.test {
      val baseTile = fakeBitmapRegionTile(sampleSize = 4)
      val tile2 = fakeBitmapRegionTile(sampleSize = 1)
      val tile3 = fakeBitmapRegionTile(sampleSize = 1)
      val tileToSkip = fakeBitmapRegionTile(sampleSize = 8)

      cache.loadOrUnloadForTiles(listOf(baseTile))

      // This tile should get overridden by the next set of
      // tiles because the throttle window hasn't passed yet.
      delay(500.milliseconds)
      cache.loadOrUnloadForTiles(listOf(tileToSkip))

      delay(500.milliseconds)
      cache.loadOrUnloadForTiles(listOf(baseTile, tile2, tile3))

      // If the same tiles are requested again within the throttle window,
      // neither the old one nor the new one should get ignored for some reason.
      cache.loadOrUnloadForTiles(listOf(baseTile, tile2, tile3))

      assertThat(awaitItem()).isEqualTo(baseTile)
      assertThat(listOf(awaitItem(), awaitItem())).containsExactly(tile2, tile3)

      scope.cancel()
    }
  }

  private fun fakeBitmapRegionTile(
    sampleSize: Int = Random.nextInt(from = 0, until = 10) * 2,
  ): BitmapRegionTile {
    val random = Random(seed = System.nanoTime())
    return BitmapRegionTile(
      sampleSize = BitmapSampleSize(sampleSize),
      bounds = IntRect(random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt())
    )
  }
}

private class FakeImageRegionDecoder : ImageRegionDecoder {
  override val imageSize: IntSize get() = error("unused")
  override val imageOrientation: ExifMetadata.ImageOrientation get() = error("unused")
  val requestedRegions = MutableSharedFlow<ImageRegionTile>()
  val decodedBitmaps = Channel<ImageBitmap>()

  override suspend fun decodeRegion(region: ImageRegionTile): ImageBitmap {
    requestedRegions.emit(region)
    return decodedBitmaps.receive()
  }

  override fun close() = Unit
}

private class FakeImageBitmap : ImageBitmap {
  override val colorSpace: ColorSpace get() = error("unused")
  override val config: ImageBitmapConfig get() = error("unused")
  override val hasAlpha: Boolean get() = error("unused")
  override val height: Int get() = error("unused")
  override val width: Int get() = error("unused")
  override fun prepareToDraw() = Unit

  override fun readPixels(
    buffer: IntArray,
    startX: Int,
    startY: Int,
    width: Int,
    height: Int,
    bufferOffset: Int,
    stride: Int
  ) = Unit
}

private suspend fun <T> ReceiveTurbine<T>.cancelAndExpectNoEvents() {
  expectNoEvents()
  assertThat(cancelAndConsumeRemainingEvents()).isEmpty()
}
