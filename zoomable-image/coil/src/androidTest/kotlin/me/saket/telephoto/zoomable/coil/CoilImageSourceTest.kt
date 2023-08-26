package me.saket.telephoto.zoomable.coil

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Dimension
import coil.test.FakeImageLoaderEngine
import com.dropbox.dropshots.Dropshots
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.util.CiScreenshotValidator
import me.saket.telephoto.util.CompositionLocalProviderReturnable
import me.saket.telephoto.util.prepareForScreenshotTest
import me.saket.telephoto.util.screenshotForMinSdk23
import me.saket.telephoto.util.waitUntil
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableImageSource.ResolveResult
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.coil.CoilImageSourceTest.SvgDecodingState.SvgDecodingDisabled
import me.saket.telephoto.zoomable.coil.CoilImageSourceTest.SvgDecodingState.SvgDecodingEnabled
import me.saket.telephoto.zoomable.image.coil.test.R
import me.saket.telephoto.zoomable.rememberZoomableImageState
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.source
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
import coil.size.Size as CoilSize

@RunWith(TestParameterInjector::class)
@OptIn(ExperimentalCoilApi::class)
class CoilImageSourceTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val timeout = Timeout.seconds(10)!!
  @get:Rule val serverRule = MockWebServerRule()
  @get:Rule val testName = TestName()
  @get:Rule val dropshots = Dropshots(
    filenameFunc = { it },
    resultValidator = CiScreenshotValidator(
      context = { rule.activity },
      tolerancePercentOnLocal = 0f,
      tolerancePercentOnCi = 0.1f,
    )
  )

  private val context: Context get() = rule.activity

  @Before
  fun setUp() {
    rule.activityRule.scenario.onActivity {
      it.prepareForScreenshotTest()
    }

    serverRule.server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return when (request.path) {
          "/placeholder_image.png" -> assetAsResponse("placeholder_image.png")
          "/full_image.png" -> assetAsResponse("full_image.png", delay = 300.milliseconds)
          "/animated_image.gif" -> assetAsResponse("animated_image.gif", delay = 300.milliseconds)
          "/emoji.svg" -> assetAsResponse("emoji.svg")
          else -> error("unknown path = ${request.path}")
        }
      }
    }
  }

  @Test fun images_should_always_be_written_to_disk(
    @TestParameter cachePolicy: CachePolicyEnum
  ) = runTest {
    resolve {
      ImageRequest.Builder(context)
        .data(serverRule.server.url("full_image.png"))
        .diskCachePolicy(cachePolicy.policy)
        .build()
    }.test {
      skipItems(1)  // Default item.
      assertThat(awaitItem().delegate).isInstanceOf(ZoomableImageSource.SubSamplingDelegate::class.java)
    }
  }

  // todo: convert to screenshot test
  @Test fun use_canvas_size_only_if_one_is_not_provided_already() = runTest {
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

  @Test fun correctly_read_crossfade_duration_and_bitmap_options() = runTest {
    resolve {
      ImageRequest.Builder(context)
        .data(serverRule.server.url("full_image.png"))
        .crossfade(9_000)
        .bitmapConfig(Bitmap.Config.RGB_565)
        .build()
    }.test {
      skipItems(1) // Default item.
      with(awaitItem()) {
        val delegate = delegate as ZoomableImageSource.SubSamplingDelegate
        assertThat(delegate.source.preview).isNotNull()
        assertThat(delegate.imageOptions).isEqualTo(ImageBitmapOptions(config = ImageBitmapConfig.Rgb565))
        assertThat(crossfadeDuration).isEqualTo(9.seconds)
      }
    }
  }

  @Test fun start_with_the_placeholder_image_then_load_the_full_image_using_subsampling() = runTest {
    // Seed the placeholder image in cache.
    val seedResult = context.imageLoader.execute(
      ImageRequest.Builder(context)
        .data(serverRule.server.url("placeholder_image.png"))
        .memoryCachePolicy(CachePolicy.ENABLED)
        //.diskCachePolicy(CachePolicy.DISABLED) https://github.com/coil-kt/coil/issues/1754
        .allowHardware(false) // Unsupported by Screenshot.capture()
        .build()
    )
    check(seedResult is SuccessResult)
    assertThat(seedResult.memoryCacheKey).isNotNull()

    val fullImageUrl = withContext(Dispatchers.IO) {
      serverRule.server.url("full_image.png")
    }

    var state: ZoomableImageState? = null
    rule.setContent {
      ZoomableAsyncImage(
        modifier = Modifier.fillMaxSize(),
        state = rememberZoomableImageState().also { state = it },
        model = ImageRequest.Builder(context)
          .data(fullImageUrl)
          .placeholderMemoryCacheKey(seedResult.memoryCacheKey)
          .allowHardware(false) // Unsupported by Screenshot.capture()
          .build(),
        contentDescription = null,
      )
    }

    rule.waitUntil(5.seconds) { state!!.isPlaceholderDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23(), testName.methodName + "_placeholder")
    }

    rule.waitUntil(5.seconds) { state!!.isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23(), testName.methodName + "_full_quality")
    }
  }

  @Test fun reload_image_when_image_request_changes() = runTest {
    var imageUrl by mutableStateOf(serverRule.server.url("placeholder_image.png"))

    var isImageDisplayed = false
    rule.setContent {
      ZoomableAsyncImage(
        state = rememberZoomableImageState().also { isImageDisplayed = it.isImageDisplayed },
        modifier = Modifier.fillMaxSize(),
        model = ImageRequest.Builder(LocalContext.current)
          .data(imageUrl)
          .allowHardware(false) // Unsupported by Screenshot.capture()
          .build(),
        contentDescription = null
      )
    }

    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23(), testName.methodName + "_first_image")
    }

    imageUrl = serverRule.server.url("full_image.png")

    rule.waitUntil(5.seconds) { !isImageDisplayed }
    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23(), testName.methodName + "_second_image")
    }
  }

  @Test fun correctly_resolve_local_images(
    @TestParameter requestData: LocalFileRequestDataParam
  ) = runTest {
    var isImageDisplayed = false
    rule.setContent {
      ZoomableAsyncImage(
        state = rememberZoomableImageState().also { isImageDisplayed = it.isImageDisplayed },
        modifier = Modifier.fillMaxSize(),
        model = ImageRequest.Builder(LocalContext.current)
          .data(requestData.data(LocalContext.current))
          .allowHardware(false) // Unsupported by Screenshot.capture()
          .build(),
        contentDescription = null
      )
    }

    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23())
    }
  }

  @Test fun correctly_resolve_svgs(
    @TestParameter requestData: SvgRequestDataParam,
    @TestParameter decodingState: SvgDecodingState,
  ) {
    val model = when (requestData) {
      SvgRequestDataParam.RemoteUrl -> serverRule.server.url("emoji.svg")
      else -> requestData.data(context)
    }
    Coil.setImageLoader(
      ImageLoader.Builder(context)
        .components {
          when (decodingState) {
            SvgDecodingEnabled -> add(SvgDecoder.Factory())
            SvgDecodingDisabled -> Unit
          }
        }
        .build()
    )

    var isImageDisplayed = false
    rule.setContent {
      ZoomableAsyncImage(
        state = rememberZoomableImageState().also { isImageDisplayed = it.isImageDisplayed },
        modifier = Modifier
          .fillMaxSize()
          .wrapContentSize()
          .size(300.dp),
        model = ImageRequest.Builder(LocalContext.current)
          .data(model)
          .allowHardware(false) // Unsupported by Screenshot.capture()
          .error(R.drawable.error_image)
          .build(),
        contentDescription = null,
      )
    }

    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23())
    }
  }

  @Test fun vector_drawables_should_not_be_sub_sampled() {
    var isImageDisplayed = false
    rule.setContent {
      ZoomableAsyncImage(
        state = rememberZoomableImageState().also { isImageDisplayed = it.isImageDisplayed },
        modifier = Modifier
          .fillMaxSize()
          .wrapContentSize()
          .size(300.dp),
        model = ImageRequest.Builder(LocalContext.current)
          .data(R.drawable.emoji)
          .allowHardware(false) // Unsupported by Screenshot.capture()
          .error(R.drawable.error_image)
          .build(),
        contentDescription = null,
      )
    }

    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23())
    }
  }

  // todo
  @Test fun show_error_drawable_if_request_fails() {
  }

  @Test fun non_bitmaps_should_not_be_sub_sampled() = runTest {
    Coil.setImageLoader(
      ImageLoader.Builder(context)
        .components { add(ImageDecoderDecoder.Factory()) }  // For GIFs.
        .build()
    )

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
    return backgroundScope.launchMolecule(mode = RecompositionMode.Immediate) {
      CompositionLocalProviderReturnable(LocalContext provides context) {
        val source = ZoomableImageSource.coil(imageRequest())
        source.resolve(flowOf(canvasSize))
      }
    }
  }

  @Suppress("unused")
  enum class CachePolicyEnum(val policy: CachePolicy) {
    CacheDisabled(CachePolicy.DISABLED),
    CacheEnabled(CachePolicy.ENABLED),
    ReadOnlyCache(CachePolicy.READ_ONLY),
    WriteOnlyCache(CachePolicy.WRITE_ONLY),
  }

  @Suppress("unused")
  enum class LocalFileRequestDataParam(val data: Context.() -> Any) {
    AssetContentUri({ Uri.parse("file:///android_asset/full_image.png") }),
    AssetContentUriString({ "file:///android_asset/full_image.png" }),
    FileContentUriWithScheme({ "file:///${createFileFromAsset("full_image.png")}" }),
    FileContentUriWithoutScheme({ "${createFileFromAsset("full_image.png")}" }),
    ResourceId({ R.drawable.full_image })
  }

  @Suppress("unused")
  enum class SvgRequestDataParam(val data: Context.() -> Any) {
    RemoteUrl({ error("unsupported") }),
    AssetContentUriSvg({ Uri.parse("file:///android_asset/emoji.svg") }),
    FileContentUriSvg({ Uri.parse("file:///${createFileFromAsset("emoji.svg")}") }),
  }

  @Suppress("unused")
  enum class SvgDecodingState {
    SvgDecodingEnabled,
    SvgDecodingDisabled,
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

  private fun assetAsResponse(
    fileName: String,
    delay: Duration = 0.seconds,
  ): MockResponse {
    val source = rule.activity.assets.open(fileName).source()
    return MockResponse()
      .addHeader("Content-Type", "image/*")
      .setBody(Buffer().apply { writeAll(source) })
      .setBodyDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
  }
}

class MockWebServerRule : ExternalResource() {
  val server = MockWebServer()
  override fun before() = server.start()
  override fun after() = server.close()
}

private fun Context.createFileFromAsset(assetName: String): Path {
  return (cacheDir.toOkioPath() / assetName).also { path ->
    FileSystem.SYSTEM.run {
      delete(path)
      write(path) { writeAll(assets.open(assetName).source()) }
    }
  }
}
