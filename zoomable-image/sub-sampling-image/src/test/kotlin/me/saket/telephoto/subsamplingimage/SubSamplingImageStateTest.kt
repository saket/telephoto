package me.saket.telephoto.subsamplingimage

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.rememberZoomableState
import okio.Buffer
import okio.Closeable
import okio.Source
import okio.Timeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowBuild
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
class SubSamplingImageStateTest {

  @Test fun `disposing rawSource during exif read does not crash and closes source afterwards`() {
    ShadowBuild.setFingerprint("android")
    // Minimal PNG header followed by a large padding chunk so ExifInterface issues multiple reads/skips.
    val pngBytes = byteArrayOf(
      0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    ) + ByteArray(64 * 1024)

    val firstReadStarted = CountDownLatch(1)
    val allowRemainingReads = CountDownLatch(1)
    val sourceClosed = CountDownLatch(1)
    val onCloseCalled = AtomicBoolean(false)

    val blockingSource = object : Source {
      private var offset = 0
      private var firstRead = true

      override fun read(sink: Buffer, byteCount: Long): Long {
        if (!firstRead) {
          allowRemainingReads.await(5, TimeUnit.SECONDS)
        }
        if (offset >= pngBytes.size) return -1L
        val toRead = if (firstRead) {
          // Return only the PNG signature on the first read so ExifInterface enters getPngAttributes()
          // and then blocks on the next read while we dispose the composition.
          firstRead = false
          firstReadStarted.countDown()
          minOf(8, pngBytes.size - offset)
        } else {
          minOf(byteCount.toInt(), pngBytes.size - offset)
        }
        sink.write(pngBytes, offset, toRead)
        offset += toRead
        return toRead.toLong()
      }

      override fun timeout(): Timeout = Timeout.NONE

      override fun close() {
        sourceClosed.countDown()
      }
    }

    val imageSource = SubSamplingImageSource.rawSource(
      source = { blockingSource },
      onClose = Closeable { onCloseCalled.set(true) },
    )

    var uncaughtError: Throwable? = null
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
      uncaughtError = throwable
    }
    val recomposerContext = SupervisorJob() + Dispatchers.Unconfined + exceptionHandler + ImmediateFrameClock
    val recomposer = Recomposer(recomposerContext)
    val recomposerScope = CoroutineScope(recomposerContext)
    val recomposerJob = recomposerScope.launch {
      recomposer.runRecomposeAndApplyChanges()
    }

    val composition = Composition(NoOpApplier, recomposer)
    try {
      composition.setContent {
        CompositionLocalProvider(
          LocalContext provides RuntimeEnvironment.getApplication(),
          LocalDensity provides Density(1f),
          LocalLayoutDirection provides LayoutDirection.Ltr,
        ) {
          rememberSubSamplingImageState(
            imageSource = imageSource,
            zoomableState = rememberZoomableState(),
          )
        }
      }

      assertThat(firstReadStarted.await(5, TimeUnit.SECONDS)).isTrue()

      // Dispose the composable while ExifMetadata.read() is still blocked mid-stream.
      composition.dispose()

      // The underlying source must stay open while the in-flight EXIF read is still active.
      assertThat(sourceClosed.count == 0L).isFalse()

      // Let the in-flight EXIF read finish; closing should now proceed without throwing.
      allowRemainingReads.countDown()

      assertThat(sourceClosed.await(5, TimeUnit.SECONDS)).isTrue()
      assertThat(onCloseCalled.get()).isTrue()
      assertThat(uncaughtError).isNull()
    } finally {
      allowRemainingReads.countDown()
      recomposer.cancel()
      recomposerJob.cancel()
      recomposerScope.cancel()
    }
  }

  @Test fun `closing rawSource before any read does not invoke source factory`() {
    var factoryInvoked = false
    var onCloseCalled = false
    val imageSource = SubSamplingImageSource.rawSource(
      source = {
        factoryInvoked = true
        Buffer()
      },
      onClose = Closeable { onCloseCalled = true },
    )

    imageSource.close()

    assertThat(factoryInvoked).isFalse()
    assertThat(onCloseCalled).isTrue()
  }

  private object ImmediateFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(0L)
  }

  private object NoOpApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
  }
}
