@file:Suppress("TestFunctionName")

package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.testIn
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class BitmapLoaderTest {
  private val decoder = FakeImageRegionDecoder()

  private fun TestScope.bitmapLoader(): BitmapLoader {
    return BitmapLoader(
      decoder = decoder,
      scope = this,
    )
  }

  @Test fun `when tiles become visible, load their bitmaps`() = runTest(timeout = 1.seconds) {
    val loader = bitmapLoader()
    val requestedRegions = decoder.requestedRegions.testIn(this)
    val cachedBitmaps = loader.cachedBitmaps().testIn(this)
    assertThat(cachedBitmaps.awaitItem()).isEmpty() // Default item.

    val visibleTile = fakeBitmapTile(isVisible = true)
    val invisibleTile = fakeBitmapTile(isVisible = false)

    loader.loadOrUnloadForTiles(listOf(visibleTile, invisibleTile))
    runCurrent()
    decoder.decodedBitmaps.emit(FakeImageBitmap())
    decoder.decodedBitmaps.emit(FakeImageBitmap())

    assertThat(requestedRegions.awaitItem()).isEqualTo(visibleTile.regionBounds)
    assertThat(cachedBitmaps.awaitItem().keys).containsExactly(visibleTile.regionBounds)

    val newlyVisibleTile = invisibleTile.copy(isVisible = true)
    loader.loadOrUnloadForTiles(listOf(visibleTile, newlyVisibleTile))
    runCurrent()
    decoder.decodedBitmaps.emit(FakeImageBitmap())

    assertThat(requestedRegions.awaitItem()).isEqualTo(newlyVisibleTile.regionBounds)
    assertThat(cachedBitmaps.awaitItem().keys).containsExactly(visibleTile.regionBounds, newlyVisibleTile.regionBounds)

    requestedRegions.cancelAndExpectNoEvents()
    cachedBitmaps.cancelAndExpectNoEvents()
  }

  @Test fun `when tiles are removed, discard their stale bitmaps from cache`() = runTest(1.seconds) {
    val loader = bitmapLoader()
    val cachedBitmaps = loader.cachedBitmaps().drop(1).testIn(this)

    val tile1 = fakeBitmapTile(isVisible = true)
    val tile2 = fakeBitmapTile(isVisible = true)
    loader.loadOrUnloadForTiles(listOf(tile1, tile2))
    runCurrent()
    decoder.decodedBitmaps.emit(FakeImageBitmap())
    decoder.decodedBitmaps.emit(FakeImageBitmap())

    assertThat(cachedBitmaps.expectMostRecentItem().keys).containsExactly(tile1.regionBounds, tile2.regionBounds)

    val tile3 = fakeBitmapTile(isVisible = true)
    loader.loadOrUnloadForTiles(listOf(tile3))
    runCurrent()
    decoder.decodedBitmaps.emit(FakeImageBitmap())

    cachedBitmaps.skipItems(1)
    assertThat(cachedBitmaps.awaitItem().keys).containsExactly(tile3.regionBounds)

    cachedBitmaps.cancelAndExpectNoEvents()
  }

  @Test fun `when tiles become invisible, discard their stale bitmaps from cache`() = runTest(1.seconds) {
    val loader = bitmapLoader()
    val cachedBitmaps = loader.cachedBitmaps().drop(1).testIn(this)

    val tile1 = fakeBitmapTile(isVisible = true)
    val tile2 = fakeBitmapTile(isVisible = true)
    loader.loadOrUnloadForTiles(listOf(tile1, tile2))
    runCurrent()
    decoder.decodedBitmaps.emit(FakeImageBitmap())
    decoder.decodedBitmaps.emit(FakeImageBitmap())

    assertThat(cachedBitmaps.expectMostRecentItem().keys).containsExactly(tile1.regionBounds, tile2.regionBounds)

    loader.loadOrUnloadForTiles(listOf(tile1, tile2.copy(isVisible = false)))
    runCurrent()

    val invisibleTile2 = tile2.copy(isVisible = false)
    val tile3 = fakeBitmapTile(isVisible = true)

    loader.loadOrUnloadForTiles(listOf(tile1, invisibleTile2, tile3))
    runCurrent()
    decoder.decodedBitmaps.emit(FakeImageBitmap())

    assertThat(cachedBitmaps.expectMostRecentItem().keys).containsExactly(tile1.regionBounds, tile3.regionBounds)

    cachedBitmaps.cancelAndExpectNoEvents()
  }

  @Test fun `when a tile becomes invisible before its bitmap could be loaded, cancel its in-flight load`() =
    runTest(timeout = 1.seconds) {
      val loader = bitmapLoader()
      val requestedRegions = decoder.requestedRegions.testIn(this)
      val cachedBitmaps = loader.cachedBitmaps().drop(1).testIn(this)

      val visibleTile = fakeBitmapTile(isVisible = true)
      loader.loadOrUnloadForTiles(listOf(visibleTile))
      runCurrent()

      assertThat(requestedRegions.awaitItem()).isEqualTo(visibleTile.regionBounds)
      cachedBitmaps.expectNoEvents()

      loader.loadOrUnloadForTiles(listOf(visibleTile.copy(isVisible = false)))
      runCurrent()

      requestedRegions.cancelAndExpectNoEvents()
      cachedBitmaps.cancelAndExpectNoEvents()

      // I don't think it's possible to uniquely identify BitmapLoader's loading jobs.
      // Checking that there aren't any active jobs should be sufficient for now.
      assertThat(coroutineContext.job.children.none { it.isActive }).isTrue()
    }

  @Test fun `when a tile is removed before its bitmap could be loaded, cancel its in-flight load`() =
    runTest(timeout = 1.seconds) {
      val loader = bitmapLoader()
      val requestedRegions = decoder.requestedRegions.testIn(this)
      val cachedBitmaps = loader.cachedBitmaps().drop(1).testIn(this)

      val visibleTile = fakeBitmapTile(isVisible = true)
      loader.loadOrUnloadForTiles(listOf(visibleTile))
      runCurrent()

      assertThat(requestedRegions.awaitItem()).isEqualTo(visibleTile.regionBounds)
      cachedBitmaps.expectNoEvents()

      loader.loadOrUnloadForTiles(emptyList())
      runCurrent()

      requestedRegions.cancelAndExpectNoEvents()
      cachedBitmaps.cancelAndExpectNoEvents()

      // Verify that BitmapLoader has cancelled all loading jobs.
      assertThat(coroutineContext.job.children.none { it.isActive }).isTrue()
    }
}

private class FakeImageRegionDecoder : ImageRegionDecoder {
  override val imageSize: Size get() = error("unused")
  val requestedRegions = MutableSharedFlow<BitmapRegionBounds>()
  val decodedBitmaps = MutableSharedFlow<ImageBitmap>()

  override suspend fun decodeRegion(region: BitmapRegionBounds, sampleSize: BitmapSampleSize): ImageBitmap {
    requestedRegions.emit(region)
    return decodedBitmaps.first()
  }
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

@OptIn(ExperimentalCoroutinesApi::class)
private fun runTest(
  timeout: Duration,
  testBody: suspend TestScope.() -> Unit
) = runTest(dispatchTimeoutMs = timeout.inWholeMilliseconds, testBody = testBody)

private fun fakeBitmapTile(isVisible: Boolean): BitmapRegionTile {
  return BitmapRegionTile(
    sampleSize = BitmapSampleSize(Random.nextInt(from = 0, until = 10) * 2),
    isVisible = isVisible,
    regionBounds = BitmapRegionBounds(
      Rect(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), Random.nextFloat())
    )
  )
}
