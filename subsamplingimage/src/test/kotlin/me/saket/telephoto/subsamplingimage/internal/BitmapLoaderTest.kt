@file:Suppress("TestFunctionName")

package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import app.cash.turbine.testIn
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Stack

@OptIn(ExperimentalCoroutinesApi::class)
class BitmapLoaderTest {
  private val decoder = FakeImageRegionDecoder()

  private fun TestScope.bitmapLoader(): BitmapLoader {
    return BitmapLoader(
      decoder = decoder,
      scope = this,
    )
  }

  @Test fun `discard stale bitmaps for tiles that are no longer present`() = runTest {
    val loader = bitmapLoader()
    val cachedBitmaps = loader.bitmaps().testIn(this)
    assertThat(cachedBitmaps.awaitItem()).isEmpty() // Default item.

    val region1 = BitmapRegionBounds(0f, 0f, 0f, 0f)
    val region2 = BitmapRegionBounds(4f, 4f, 4f, 4f)

    loader.loadOrUnloadForTiles(
      listOf(
        BitmapTile(
          sampleSize = BitmapSampleSize(2),
          regionBounds = region1,
        ),
        BitmapTile(
          sampleSize = BitmapSampleSize(2),
          regionBounds = region2,
        )
      )
    )
    runCurrent()
    assertThat(cachedBitmaps.expectMostRecentItem().keys).containsExactly(region1, region2)

    loader.loadOrUnloadForTiles(
      listOf(
        BitmapTile(
          sampleSize = BitmapSampleSize(1),
          regionBounds = BitmapRegionBounds(8f, 8f, 8f, 8f),
        )
      )
    )
    cachedBitmaps.skipItems(1)
    cachedBitmaps.awaitItem().keys.let {
      assertThat(it).doesNotContain(region1)
      assertThat(it).doesNotContain(region2)
    }

    cachedBitmaps.ensureAllEventsConsumed()
    cachedBitmaps.cancel()
  }

  @Test fun `cancel ongoing loads when tile is no longer visible`() {
    // TODO.
  }

  @Test fun `cancel ongoing loads when tile is removed`() {
    // TODO.
  }
}

private fun <T> Stack<T>.popAll(): List<T> {
  return buildList {
    repeat(size) {
      add(pop())
    }
  }
}

private class FakeImageRegionDecoder : ImageRegionDecoder {
  override val imageSize: Size get() = error("unused")
  val requestedRegions = Stack<BitmapRegionBounds>()

  override suspend fun decodeRegion(region: BitmapRegionBounds, sampleSize: BitmapSampleSize): ImageBitmap {
    requestedRegions.push(region)
    return FakeImageBitmap()
  }
}

private fun BitmapRegionBounds(
  left: Float,
  top: Float,
  right: Float,
  bottom: Float,
) = BitmapRegionBounds(Rect(left, top, right, bottom))

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
