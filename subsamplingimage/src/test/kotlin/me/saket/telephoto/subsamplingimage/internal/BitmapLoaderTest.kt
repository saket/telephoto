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

  @Test fun `when tiles are received, load bitmaps only for new tiles`() = runTest(timeout = 1.seconds) {
    val loader = bitmapLoader()
    val decodedRegions = decoder.decodedRegions.testIn(this)
    val cachedBitmaps = loader.cachedBitmaps().testIn(this)
    assertThat(cachedBitmaps.awaitItem()).isEmpty() // Default item.

    val tile1 = fakeBitmapRegionTile()
    val tile2 = fakeBitmapRegionTile()

    loader.loadOrUnloadForTiles(listOf(tile1, tile2))
    runCurrent()
    decoder.decodedBitmaps.emit(FakeImageBitmap())
    decoder.decodedBitmaps.emit(FakeImageBitmap())

    assertThat(decodedRegions.awaitItem()).isEqualTo(tile1)
    assertThat(decodedRegions.awaitItem()).isEqualTo(tile2)
    cachedBitmaps.skipItems(1)
    assertThat(cachedBitmaps.awaitItem().keys).containsExactly(tile1, tile2)

    val tile3 = fakeBitmapRegionTile()
    loader.loadOrUnloadForTiles(listOf(tile1, tile2, tile3))
    runCurrent()
    decoder.decodedBitmaps.emit(FakeImageBitmap())

    assertThat(decodedRegions.awaitItem()).isEqualTo(tile3)
    assertThat(cachedBitmaps.awaitItem().keys).containsExactly(tile1, tile2, tile3)

    decodedRegions.cancelAndExpectNoEvents()
    cachedBitmaps.cancelAndExpectNoEvents()
  }

  @Test fun `when tiles are removed, discard their stale bitmaps from cache`() = runTest(1.seconds) {
    val loader = bitmapLoader()
    val cachedBitmaps = loader.cachedBitmaps().drop(1).testIn(this)

    val tile1 = fakeBitmapRegionTile()
    val tile2 = fakeBitmapRegionTile()
    loader.loadOrUnloadForTiles(listOf(tile1, tile2))
    runCurrent()
    decoder.decodedBitmaps.emit(FakeImageBitmap())
    decoder.decodedBitmaps.emit(FakeImageBitmap())

    assertThat(cachedBitmaps.expectMostRecentItem().keys).containsExactly(tile1, tile2)

    val tile3 = fakeBitmapRegionTile()
    loader.loadOrUnloadForTiles(listOf(tile3))
    runCurrent()
    decoder.decodedBitmaps.emit(FakeImageBitmap())

    cachedBitmaps.skipItems(1)
    assertThat(cachedBitmaps.awaitItem().keys).containsExactly(tile3)

    cachedBitmaps.cancelAndExpectNoEvents()
  }

  @Test fun `when a tile is removed before its bitmap could be loaded, cancel its in-flight load`() =
    runTest(timeout = 1.seconds) {
      val loader = bitmapLoader()
      val decodedRegions = decoder.decodedRegions.testIn(this)
      val cachedBitmaps = loader.cachedBitmaps().drop(1).testIn(this)

      val visibleTile = fakeBitmapRegionTile()
      loader.loadOrUnloadForTiles(listOf(visibleTile))
      runCurrent()

      assertThat(decodedRegions.awaitItem()).isEqualTo(visibleTile)
      cachedBitmaps.expectNoEvents()

      loader.loadOrUnloadForTiles(emptyList())
      runCurrent()

      decodedRegions.cancelAndExpectNoEvents()
      cachedBitmaps.cancelAndExpectNoEvents()

      // Verify that BitmapLoader has cancelled all loading jobs.
      // I don't think it's possible to uniquely identify BitmapLoader's loading jobs.
      // Checking that there aren't any active jobs should be sufficient for now.
      assertThat(coroutineContext.job.children.none { it.isActive }).isTrue()
    }

  private fun fakeBitmapRegionTile(): BitmapRegionTile {
    val random = Random(seed = System.nanoTime())
    return BitmapRegionTile(
      sampleSize = BitmapSampleSize(random.nextInt(from = 0, until = 10) * 2),
      bounds = Rect(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat())
    )
  }
}

private class FakeImageRegionDecoder : ImageRegionDecoder {
  override val imageSize: Size get() = error("unused")
  val decodedRegions = MutableSharedFlow<BitmapRegionTile>()
  val decodedBitmaps = MutableSharedFlow<ImageBitmap>()

  override suspend fun decodeRegion(region: BitmapRegionTile): ImageBitmap {
    println("decodeRegion(region=$region)")
    decodedRegions.emit(region)
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
