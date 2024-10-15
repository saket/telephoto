@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
@file:OptIn(DelicateCoilApi::class)

package me.saket.telephoto.zoomable.coil3

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotInstanceOf
import assertk.assertions.isNotNull
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.gif.AnimatedImageDecoder
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.request.colorSpace
import coil3.request.crossfade
import coil3.request.error
import coil3.size.Dimension
import coil3.svg.SvgDecoder
import coil3.test.FakeImageLoaderEngine
import com.dropbox.dropshots.Dropshots
import com.google.modernstorage.storage.AndroidFileSystem
import com.google.modernstorage.storage.toOkioPath
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import leakcanary.LeakAssertions
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.util.CiScreenshotValidator
import me.saket.telephoto.util.compositionLocalProviderReturnable
import me.saket.telephoto.util.prepareForScreenshotTest
import me.saket.telephoto.util.waitUntil
import me.saket.telephoto.zoomable.ZoomableImageSource
import me.saket.telephoto.zoomable.ZoomableImageSource.ResolveResult
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.coil3.Coil3ImageSourceTest.SvgDecodingState.SvgDecodingDisabled
import me.saket.telephoto.zoomable.coil3.Coil3ImageSourceTest.SvgDecodingState.SvgDecodingEnabled
import me.saket.telephoto.zoomable.image.coil3.test.R
import me.saket.telephoto.zoomable.rememberZoomableImageState
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.FileSystem
import okio.Path
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
import android.graphics.ColorSpace as AndroidColorSpace
import coil3.size.Size as CoilSize

@RunWith(TestParameterInjector::class)
class Coil3ImageSourceTest {
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
          "/single_frame_gif.gif" -> assetAsResponse("single_frame_gif.gif", delay = 300.milliseconds)
          "/emoji.svg" -> assetAsResponse("emoji.svg")
          else -> error("unknown path = ${request.path}")
        }
      }
    }
  }

  @After
  fun tearDown() {
    LeakAssertions.assertNoLeaks()

    SingletonImageLoader.get(context).diskCache?.clear()
    SingletonImageLoader.reset()
  }

  @Test fun images_should_always_be_written_to_disk(
    @TestParameter cachePolicy: CachePolicyEnum
  ) = runTest {
    resolve {
      ImageRequest.Builder(context)
        .data(serverRule.server.url("full_image.png").toString())
        .diskCachePolicy(cachePolicy.policy)
        .build()
    }.test {
      skipItems(1)  // Default item.
      assertThat(awaitItem().delegate!!).isInstanceOf(ZoomableImageSource.SubSamplingDelegate::class.java)
    }
  }

  // todo: convert to screenshot test
  @Test fun use_canvas_size_only_if_one_is_not_provided_already() = runTest {
    val requests = MutableStateFlow<ImageRequest?>(null)

    SingletonImageLoader.setUnsafe(buildImageLoader {
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
        .data(serverRule.server.url("full_image.png").toString())
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
        .data(serverRule.server.url("full_image.png").toString())
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
        .data(serverRule.server.url("full_image.png").toString())
        .crossfade(9_000)
        .bitmapConfig(Bitmap.Config.RGB_565)
        .colorSpace(AndroidColorSpace.get(AndroidColorSpace.Named.SRGB))
        .build()
    }.test {
      skipItems(1) // Default item.
      with(awaitItem()) {
        val delegate = delegate as ZoomableImageSource.SubSamplingDelegate
        assertThat(delegate.source.preview).isNotNull()
        assertThat(delegate.imageOptions).isEqualTo(
          ImageBitmapOptions(
            config = ImageBitmapConfig.Rgb565,
            colorSpace = ColorSpaces.Srgb,
          )
        )
        assertThat(crossfadeDuration).isEqualTo(9.seconds)
      }
    }
  }

  @Test fun start_with_the_placeholder_image_then_load_the_full_image_using_subsampling() = runTest {
    // Seed the placeholder image in memory cache.
    val seedResult = context.imageLoader.execute(
      ImageRequest.Builder(context)
        .data(serverRule.server.url("placeholder_image.png").toString())
        .memoryCachePolicy(CachePolicy.ENABLED)
        //.diskCachePolicy(CachePolicy.DISABLED) https://github.com/coil-kt/coil/issues/1754
        .allowHardware(false) // Unsupported by Screenshot.capture()
        .build()
    )
    check(seedResult is SuccessResult)
    assertThat(seedResult.memoryCacheKey).isNotNull()

    val fullImageUrl = withContext(Dispatchers.IO) {
      serverRule.server.url("full_image.png").toString()
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
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_placeholder")
    }

    rule.waitUntil(5.seconds) { state!!.isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_full_quality")
    }
  }

  @Test fun reload_image_when_image_request_changes() = runTest {
    var imageUrl by mutableStateOf(serverRule.server.url("placeholder_image.png").toString())

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
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_first_image")
    }

    imageUrl = serverRule.server.url("full_image.png").toString()

    rule.waitUntil(5.seconds) { !isImageDisplayed }
    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_second_image")
    }
  }

  @Test fun correctly_resolve_local_images(
    @TestParameter requestData: LocalFileRequestDataParam
  ) = runTest {
    lateinit var imageState: ZoomableImageState
    rule.setContent {
      ZoomableAsyncImage(
        state = rememberZoomableImageState().also { imageState = it },
        modifier = Modifier.fillMaxSize(),
        model = ImageRequest.Builder(LocalContext.current)
          .data(requestData.data(LocalContext.current))
          .allowHardware(false) // Unsupported by Screenshot.capture()
          .build(),
        contentDescription = null
      )
    }

    rule.waitUntil(5.seconds) { imageState.isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }

    assertThat(imageState.subSamplingState).isNotNull()
  }

  @Test fun correctly_resolve_svgs(
    @TestParameter requestData: SvgRequestDataParam,
    @TestParameter decodingState: SvgDecodingState,
  ) {
    val model = when (requestData) {
      SvgRequestDataParam.RemoteUrl -> serverRule.server.url("emoji.svg").toString()
      else -> requestData.data(context)
    }
    SingletonImageLoader.setUnsafe(
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
      dropshots.assertSnapshot(rule.activity)
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
      dropshots.assertSnapshot(rule.activity)
    }
  }

  // Regression test for https://github.com/saket/telephoto/issues/99.
  @Test fun show_error_drawable_if_a_local_image_cached_in_memory_no_longer_exists() = runTest {
    val imageInExternalStorage: Uri = context.copyImageToExternalStorage(
      context.createFileFromAsset("full_image.png")
    )

    lateinit var imageState: ZoomableImageState
    val stateRestorer = StateRestorationTester(rule)
    stateRestorer.setContent {
      ZoomableAsyncImage(
        state = rememberZoomableImageState().also { imageState = it },
        modifier = Modifier.fillMaxSize(),
        model = ImageRequest.Builder(LocalContext.current)
          .data(imageInExternalStorage)
          .error(R.drawable.error_image)
          .allowHardware(false) // Unsupported by Screenshot.capture()
          .build(),
        contentDescription = null
      )
    }

    rule.waitUntil { imageState.isImageDisplayed }
    AndroidFileSystem(context).delete(imageInExternalStorage.toOkioPath())

    stateRestorer.emulateSavedInstanceStateRestore()

    rule.waitUntil { imageState.isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun gifs_should_not_be_sub_sampled(
    @TestParameter param: GifRequestDataParam
  ) = runTest {
    SingletonImageLoader.setUnsafe(
      ImageLoader.Builder(context)
        .components { add(AnimatedImageDecoder.Factory()) }  // For GIFs.
        .build()
    )

    resolve {
      serverRule.server.url(param.url).toString()
    }.test {
      skipItems(1) // Default item.
      assertThat(awaitItem().delegate!!).isNotInstanceOf(ZoomableImageSource.SubSamplingDelegate::class.java)
    }
  }

  // Regression test for https://github.com/saket/telephoto/issues/37.
  @Test fun reload_image_if_its_evicted_from_the_disk_cache_but_is_still_present_in_the_memory_cache() = runTest {
    val memoryCache = context.imageLoader.memoryCache!!
    val diskCache = context.imageLoader.diskCache!!

    // Seed the image in both caches.
    val imageUrl: String = serverRule.server.url("full_image.png").toString()
    context.imageLoader.execute(
      ImageRequest.Builder(context)
        .data(imageUrl)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build()
    )
    assertThat(memoryCache[MemoryCache.Key(imageUrl)]).isNotNull()
    diskCache.openSnapshot(imageUrl).let { snapshot ->
      assertThat(snapshot).isNotNull()
      snapshot!!.close()
    }

    // Clear only the disk cache.
    diskCache.fileSystem.deleteRecursively(diskCache.directory)
    assertThat(memoryCache[MemoryCache.Key(imageUrl)]).isNotNull()

    // Make sure that the image gets reloaded from the network.
    resolve { imageUrl }.test {
      skipItems(1) // Default item.
      assertThat(awaitItem().delegate!!).isInstanceOf(ZoomableImageSource.SubSamplingDelegate::class.java)
    }
  }

  // Regression test for https://github.com/saket/telephoto/issues/50
  @Test fun image_url_with_nocache_http_header() = runTest {
    serverRule.server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return assetAsResponse("full_image.png")
          .addHeader("Cache-Control", "private, no-cache, no-store, must-revalidate")
      }
    }

    lateinit var imageState: ZoomableImageState
    val fullImageUrl: String = withContext(Dispatchers.IO) {
      serverRule.server.url("full_image.png").toString()
    }

    val stateRestorer = StateRestorationTester(rule)
    stateRestorer.setContent {
      ZoomableAsyncImage(
        state = rememberZoomableImageState().also { imageState = it },
        modifier = Modifier
          .fillMaxSize()
          .wrapContentSize()
          .size(300.dp),
        model = ImageRequest.Builder(LocalContext.current)
          .data(fullImageUrl)
          .allowHardware(false) // Unsupported by Screenshot.capture()
          .error(R.drawable.error_image)
          .build(),
        contentDescription = null,
      )
    }

    rule.waitUntil(5.seconds) { imageState.isImageDisplayed }
    assertThat(imageState.subSamplingState).isNotNull()

    // Bug description: the image loads from the network on the first load and the memory cache
    // on the second load. The second load crashes the app because telephoto incorrectly tries
    // to load it as a content URI.
    // Reproduction steps taken from https://github.com/saket/telephoto/issues/50.
    stateRestorer.emulateSavedInstanceStateRestore()
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
      assertThat(imageState.subSamplingState).isNotNull()
    }
  }

  @Test fun image_is_not_reloaded_on_every_composition_when_image_request_contains_unstable_params() = runTest {
    lateinit var imageState: ZoomableImageState
    val fullImageUrl: String = withContext(Dispatchers.IO) {
      serverRule.server.url("full_image.png").toString()
    }

    var loadCount = 0
    var compositionCount = 0

    rule.setContent {
      val counter by produceState(initialValue = 0) {
        while (true) {
          this.value += 1
          delay(50.milliseconds)
        }
      }
      BasicText(text = counter.toString())

      SideEffect {
        compositionCount++
      }

      ZoomableAsyncImage(
        state = rememberZoomableImageState().also { imageState = it },
        modifier = Modifier
          .fillMaxSize()
          .wrapContentSize()
          .size(300.dp),
        model = ImageRequest.Builder(LocalContext.current)
          .data(fullImageUrl)
          .listener(onStart = { loadCount++ }) // <- Causes instability by creating a new listener on every composition.
          .placeholder(ColorDrawable(0xDEADBEEF.toInt()).asImage()) // <- Creates a new drawable on every composition.
          .build(),
        contentDescription = null,
      )
    }

    rule.waitUntil {
      imageState.isImageDisplayed && compositionCount >= 10
    }
    assertThat(loadCount).isEqualTo(1)
  }

  context(TestScope)
  private fun resolve(
    canvasSize: Size = Size(1080f, 1920f),
    imageRequestProvider: @Composable () -> Any
  ): StateFlow<ResolveResult> {
    return backgroundScope.launchMolecule(mode = RecompositionMode.Immediate) {
      compositionLocalProviderReturnable(LocalContext provides context) {
        val imageRequest = imageRequestProvider()
        if (imageRequest is HttpUrl || imageRequest is ImageRequest && imageRequest.data is HttpUrl) {
          error("coil3 does not support HttpUrls anymore")
        }
        val source = ZoomableImageSource.coil(imageRequest)
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
  enum class GifRequestDataParam(val url: String) {
    AnimatedGif("animated_image.gif"),
    SingleFrameGif("single_frame_gif.gif"),
  }

  @Suppress("unused")
  enum class SvgDecodingState {
    SvgDecodingEnabled,
    SvgDecodingDisabled,
  }

  private fun buildImageLoader(build: ImageLoader.Builder.() -> ImageLoader.Builder): ImageLoader =
    ImageLoader.Builder(context)
      .let(build)
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

private suspend fun Context.copyImageToExternalStorage(imageFile: Path): Uri {
  val fs = AndroidFileSystem(this)
  val uri = fs.createMediaStoreUri(
    filename = imageFile.name,
    collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
    directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
  )!!
  fs.write(uri.toOkioPath()) {
    fs.read(imageFile) {
      writeAll(this)
    }
  }
  fs.scanUri(uri, mimeType = "image/png")
  return uri
}
