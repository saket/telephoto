package me.saket.telephoto.zoomable.glide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.cash.molecule.RecompositionClock
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.google.accompanist.drawablepainter.DrawablePainter
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.util.BitmapSubject.Companion.assertThat
import me.saket.telephoto.util.CompositionLocalProviderReturnable
import me.saket.telephoto.util.prepareForScreenshotTest
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableImageSource.ResolveResult
import me.saket.telephoto.zoomable.ZoomableImageSource.SubSamplingDelegate
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.source
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.TestName
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import com.bumptech.glide.load.engine.cache.DiskCache as GlideDiskCache

/** Note to self: this should ideally be a junit test, but Glide was unable to decode HTTP responses in a fake environment. */
@RunWith(TestParameterInjector::class)
@OptIn(ExperimentalCoroutinesApi::class)
class GlideImageSourceTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
  private val context: Context get() = rule.activity

  @get:Rule val timeout = Timeout.seconds(10)!!
  @get:Rule val serverRule = MockWebServerRule()
  @get:Rule val testName = TestName()

  @Before
  fun setup() {
    rule.activityRule.scenario.onActivity {
      it.prepareForScreenshotTest()
    }

    serverRule.server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest) = when (request.path) {
        "/full_image.png" -> rawResourceAsResponse("full_image.png", delay = 500.milliseconds)
        "/placeholder_image.png" -> rawResourceAsResponse("placeholder_image.png")
        "/animated_image.gif" -> rawResourceAsResponse("animated_image.gif")
        else -> error("unknown path = ${request.path}")
      }
    }
  }

  @After
  fun tearDown() {
    Glide.with(rule.activity.baseContext).let {
      it.onStop()
      it.onDestroy()
    }
    Glide.tearDown()
  }

  @Test fun images_should_always_be_written_to_disk(
    @TestParameter strategyParam: DiskCacheStrategyParam
  ) = runTest {
    val diskCacheDir = context.cacheDir.toOkioPath() / GlideDiskCache.Factory.DEFAULT_DISK_CACHE_DIR
    val imageUrl = serverRule.server.url("full_image.png").toString()

    resolve(
      model = { imageUrl },
      requestBuilder = { it.diskCacheStrategy(strategyParam.strategy) }
    ).test {
      skipItems(1)  // Default item.
      assertThat(FileSystem.SYSTEM.list(diskCacheDir)).isEmpty()

      assertThat(awaitItem().delegate).isInstanceOf(SubSamplingDelegate::class.java)
      assertThat(FileSystem.SYSTEM.list(diskCacheDir)).isNotEmpty()
    }
  }

  @Test fun correctly_read_crossfade_duration_and_bitmap_options() = runTest {
    resolve(
      model = { serverRule.server.url("full_image.png").toString() },
      requestBuilder = {
        it.transition(withCrossFade(9_000))
          .disallowHardwareConfig()
      }
    ).test {
      // Default item.
      assertThat(awaitItem()).isEqualTo(ResolveResult(delegate = null))

      with(awaitItem()) {
        val delegate = delegate as SubSamplingDelegate
        assertThat(delegate.imageOptions).isEqualTo(ImageBitmapOptions(config = ImageBitmapConfig.Argb8888))
        assertThat(crossfadeDuration).isEqualTo(9.seconds)
      }
    }
  }

  @Test fun start_with_the_placeholder_image_then_load_the_full_image_using_subsampling() = runTest {
    val seededPlaceholder = seedMemoryCacheWith(serverRule.server.url("placeholder_image.png"))

    resolve(
      model = { serverRule.server.url("full_image.png").toString() },
      requestBuilder = {
        it.placeholder(seededPlaceholder)
      }
    ).test {
      // Default item.
      assertThat(awaitItem()).isEqualTo(ResolveResult(delegate = null))

      val thumbnailItem = awaitItem().apply {
        assertThat(delegate).isNull() // Full image shouldn't load yet because the HTTP response is delayed.
        assertThat(placeholder!!.extractBitmap()).hasSamePixelsAs(seededPlaceholder.extractBitmap())
      }

      with(awaitItem()) {
        assertThat(placeholder).isEqualTo(thumbnailItem.placeholder)
      }
    }
  }

  @Test fun start_with_the_thumbnail_image_then_load_the_full_image_using_subsampling() = runTest {
    val fullImageUrl = serverRule.server.url("full_image.png")
    val thumbnailImageUrl = serverRule.server.url("placeholder_image.png")

    resolve(
      model = { fullImageUrl.toString() },
      requestBuilder = {
        it.thumbnail(
          Glide.with(context).load(thumbnailImageUrl.toString())
        )
      }
    ).test {
      // Default item.
      assertThat(awaitItem()).isEqualTo(ResolveResult(delegate = null))

      val thumbnailItem = awaitItem().apply {
        assertThat(delegate).isNull() // Full image shouldn't load yet because the HTTP response is delayed.
      }
      with(awaitItem()) {
        assertThat(delegate).isInstanceOf(SubSamplingDelegate::class.java)
        assertThat(placeholder).isEqualTo(thumbnailItem.placeholder)
      }
    }
  }

  @Test fun reload_image_when_image_request_changes() = runTest {
    var imageUrl by mutableStateOf("placeholder_image.png")

    resolve(model = { serverRule.server.url(imageUrl).toString() }).test {
      assertThat(awaitItem()).isEqualTo(ResolveResult(delegate = null)) // Default item.
      skipItems(1)

      imageUrl = "full_image.png"
      assertThat(awaitItem()).isEqualTo(ResolveResult(delegate = null)) // Default item for new image.
    }
  }

  // todo
  @Test fun show_error_drawable_if_request_fails() = runTest {
  }

  @Test fun non_bitmaps_should_not_be_sub_sampled() = runTest {
    resolve(
      model = { serverRule.server.url("animated_image.gif").toString() }
    ).test {
      skipItems(1) // Default item.
      assertThat(awaitItem().delegate).isNotInstanceOf(SubSamplingDelegate::class.java)
    }
  }

  private suspend fun seedMemoryCacheWith(imageUrl: HttpUrl): Drawable {
    return withContext(Dispatchers.IO) {
      Glide.with(context)
        .load(imageUrl.toString())
        .skipMemoryCache(false)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .submit()
        .get()
    }
  }

  @Suppress("unused")
  enum class DiskCacheStrategyParam(val strategy: DiskCacheStrategy) {
    None(DiskCacheStrategy.NONE),
    All(DiskCacheStrategy.ALL),
    Data(DiskCacheStrategy.DATA),
    Automatic(DiskCacheStrategy.AUTOMATIC),
    Resource(DiskCacheStrategy.RESOURCE),
  }

  context(TestScope)
  private fun resolve(
    model: () -> Any?,
    requestBuilder: (RequestBuilder<Drawable>) -> RequestBuilder<Drawable> = { it },
    canvasSize: Size = Size(1080f, 1920f)
  ): Flow<ResolveResult> {
    return backgroundScope.launchMolecule(clock = RecompositionClock.Immediate) {
      CompositionLocalProviderReturnable(LocalContext provides rule.activity) {
        val source = ZoomableImageSource.glide(model(), requestBuilder)
        source.resolve(flowOf(canvasSize))
      }
    }
  }

  private fun rawResourceAsResponse(
    fileName: String,
    delay: Duration = 0.seconds,
  ): MockResponse {
    val source = rule.activity.assets.open(fileName).source()
    return MockResponse()
      .addHeader("Content-Type", "image/png")
      .setBody(Buffer().apply { writeAll(source) })
      .setBodyDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
  }
}

class MockWebServerRule : ExternalResource() {
  val server = MockWebServer()
  override fun before() = server.start()
  override fun after() = server.close()
}

private fun Drawable.extractBitmap(): Bitmap {
  return (this as BitmapDrawable).bitmap
}

private fun Painter.extractBitmap(): Bitmap {
  return (this as DrawablePainter).drawable.extractBitmap()
}
