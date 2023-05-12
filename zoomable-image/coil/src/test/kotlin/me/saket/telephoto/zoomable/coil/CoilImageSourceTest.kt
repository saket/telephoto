package me.saket.telephoto.zoomable.coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import app.cash.molecule.RecompositionClock
import app.cash.molecule.launchMolecule
import app.cash.paparazzi.Paparazzi
import app.cash.turbine.test
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Dimension
import coil.test.FakeImageLoaderEngine
import com.google.accompanist.drawablepainter.DrawablePainter
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.util.BitmapSubject.Companion.assertThat
import me.saket.telephoto.util.CompositionLocalProviderReturnable
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableImageSource.ResolveResult
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import coil.size.Size as CoilSize

@RunWith(TestParameterInjector::class)
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalCoilApi::class)
class CoilImageSourceTest {
  // Only used to create a fake context. I could use robolectric but it's
  // not super reliable and I'm already using paparazzi in other tests.
  @get:Rule val paparazzi = Paparazzi()
  private val context: Context get() = paparazzi.context

  @get:Rule val timeout = Timeout.seconds(10)!!
  @get:Rule val serverRule = MockWebServerRule()
  private val diskCacheSystem = FakeFileSystem()

  @Before
  fun setUp() {
    Dispatchers.setMain(Dispatchers.Unconfined)

    Coil.setImageLoader(
      ImageLoader.Builder(context)
        .networkObserverEnabled(false)
        .diskCache(
          DiskCache.Builder()
            .directory(diskCacheSystem.workingDirectory)
            .fileSystem(diskCacheSystem)
            .build()
        )
        .components { add(ImageDecoderDecoder.Factory()) }
        .build()
    )

    serverRule.server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return when (request.path) {
          "/placeholder_image.png" -> rawResourceAsResponse("placeholder_image.png")
          "/full_image.png" -> rawResourceAsResponse("full_image.png", delay = 300.milliseconds)
          "/animated_image.gif" -> rawResourceAsResponse("animated_image.gif", delay = 300.milliseconds)
          else -> error("unknown path = ${request.path}")
        }
      }
    }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test fun `images should always be written to disk`(
    @TestParameter cachePolicy: CachePolicy
  ) = runTest {
    val diskCache = context.imageLoader.diskCache!!
    val diskCacheKey = "full_image_disk_cache_key"

    resolve {
      ImageRequest.Builder(context)
        .data(serverRule.server.url("full_image.png"))
        .diskCachePolicy(cachePolicy)
        .diskCacheKey(diskCacheKey)
        .build()
    }.test {
      skipItems(1)  // Default item.
      assertThat(awaitItem().delegate).isInstanceOf(ZoomableImageSource.SubSamplingDelegate::class.java)
      assertThat(diskCache[diskCacheKey]).isNotNull()
    }
  }

  @Test fun `use canvas size only if one is not provided already`() = runTest {
    val requests = MutableStateFlow<ImageRequest?>(null)

    Coil.setImageLoader(buildImageLoader {
      components {
        add(buildFakeImageEngine {
          addInterceptor {
            requests.tryEmit(it.request)
            null
          }
        })
      }
    })

    resolve(canvasSize = Size(100f, 100f)) {
      ImageRequest.Builder(context)
        .data(serverRule.server.url("full_image.png"))
        .size(5, 6)
        .build()
    }.test {
      skipItems(1)  // Default item.
      assertThat(awaitItem().delegate).isNotNull()

      assertThat(requests.value!!.sizeResolver.size()).isEqualTo(
        CoilSize(Dimension.Pixels(5), Dimension.Pixels(6))
      )
    }

    resolve(canvasSize = Size(100f, 200f)) {
      ImageRequest.Builder(context)
        .data(serverRule.server.url("full_image.png"))
        .build()
    }.test {
      skipItems(1)  // Default item.
      assertThat(awaitItem().delegate).isNotNull()

      assertThat(requests.value!!.sizeResolver.size()).isEqualTo(
        CoilSize(Dimension.Pixels(100), Dimension.Pixels(200))
      )
    }
  }

  @Test fun `start with the placeholder image then load the full image using subsampling`() = runTest {
    // Seed the placeholder image in cache.
    val seedResult = context.imageLoader.execute(
      ImageRequest.Builder(context)
        .data(serverRule.server.url("placeholder_image.png"))
        .memoryCachePolicy(CachePolicy.ENABLED)
        //.diskCachePolicy(CachePolicy.DISABLED) https://github.com/coil-kt/coil/issues/1754
        .build()
    )
    check(seedResult is SuccessResult)
    assertThat(seedResult.memoryCacheKey).isNotNull()
    val seededBitmap = seedResult.drawable.extractBitmap()

    val diskCache = context.imageLoader.diskCache!!
    val diskCacheKey = "full_image_disk_cache_key"

    resolve {
      ImageRequest.Builder(context)
        .data(serverRule.server.url("full_image.png"))
        .placeholderMemoryCacheKey(seedResult.memoryCacheKey)
        .crossfade(9_000)
        .allowHardware(false)
        .diskCacheKey(diskCacheKey)
        .build()
    }.test {
      // Default item.
      assertThat(awaitItem()).isEqualTo(ResolveResult(delegate = null))

      val placeholderItem = awaitItem().apply {
        assertThat(delegate).isNull() // Full image shouldn't load yet because the HTTP response is delayed.
        assertThat(placeholder!!.extractBitmap()).hasSamePixelsAs(seededBitmap)
      }

      assertThat(awaitItem()).isEqualTo(
        ResolveResult(
          delegate = ZoomableImageSource.SubSamplingDelegate(
            source = SubSamplingImageSource.file(diskCache[diskCacheKey]!!.data),
            imageOptions = ImageBitmapOptions(config = ImageBitmapConfig.Argb8888),
          ),
          crossfadeDuration = 9.seconds,
          placeholder = placeholderItem.placeholder
        )
      )
    }
  }

  @Test fun `reload image when image request changes`() = runTest {
    var imageUrl by mutableStateOf("placeholder_image.png")

    resolve {
      serverRule.server.url(imageUrl)
    }.test {
      assertThat(awaitItem()).isEqualTo(ResolveResult(delegate = null)) // Default item.
      skipItems(1)

      imageUrl = "full_image.png"
      assertThat(awaitItem()).isEqualTo(ResolveResult(delegate = null)) // Default item for new image.
    }
  }

  // todo
  @Test fun `show error drawable if request fails`() {
  }

  @Test fun `non-bitmaps should not be sub-sampled`() = runTest {
    resolve {
      serverRule.server.url("animated_image.gif")
    }.test {
      skipItems(1) // Default item.
      assertThat(awaitItem().delegate).isNotInstanceOf(ZoomableImageSource.SubSamplingDelegate::class.java)
    }
  }

  context(TestScope)
  private fun resolve(
    canvasSize: Size = Size(1080f, 1920f),
    imageRequest: @Composable () -> Any
  ): StateFlow<ResolveResult> {
    return backgroundScope.launchMolecule(clock = RecompositionClock.Immediate) {
      CompositionLocalProviderReturnable(LocalContext provides context) {
        val source = ZoomableImageSource.coil(imageRequest())
        source.resolve(flowOf(canvasSize))
      }
    }
  }

  private fun buildImageLoader(build: ImageLoader.Builder.() -> ImageLoader.Builder): ImageLoader =
    ImageLoader.Builder(context)
      .let(build)
      .networkObserverEnabled(false)
      .build()

  private fun buildFakeImageEngine(
    build: FakeImageLoaderEngine.Builder.() -> FakeImageLoaderEngine.Builder
  ) = FakeImageLoaderEngine.Builder()
    .let(build)
    .build()
}

class MockWebServerRule : ExternalResource() {
  val server = MockWebServer()
  override fun before() = server.start()
  override fun after() = server.close()
}

private fun rawResourceAsResponse(
  fileName: String,
  delay: Duration = 0.seconds,
): MockResponse {
  val source = FileSystem.RESOURCES.source(fileName.toPath())
  return MockResponse()
    .addHeader("Content-Type", "image/png")
    .setBody(Buffer().apply { writeAll(source) })
    .setBodyDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
}

private fun Drawable.extractBitmap(): Bitmap {
  return (this as BitmapDrawable).bitmap
}

private fun Painter.extractBitmap(): Bitmap {
  return (this as DrawablePainter).drawable.extractBitmap()
}
