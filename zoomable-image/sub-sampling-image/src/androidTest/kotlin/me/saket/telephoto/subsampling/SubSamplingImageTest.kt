@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.subsampling

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.dropbox.dropshots.Dropshots
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.common.truth.TruthJUnit
import com.google.common.truth.TruthJUnit.assume
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImageState
import me.saket.telephoto.subsamplingimage.internal.AndroidImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.BitmapRegionTile
import me.saket.telephoto.subsamplingimage.internal.BitmapSampleSize
import me.saket.telephoto.subsamplingimage.internal.CanvasRegionTile
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.LocalImageRegionDecoderFactory
import me.saket.telephoto.subsamplingimage.internal.PooledImageRegionDecoder
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.subsamplingimage.test.R
import me.saket.telephoto.util.CiScreenshotValidator
import me.saket.telephoto.util.assertSnapshot
import me.saket.telephoto.util.prepareForScreenshotTest
import me.saket.telephoto.util.screenshotForMinSdk23
import me.saket.telephoto.util.waitUntil
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableContentTransformation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.source
import org.junit.AssumptionViolatedException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(TestParameterInjector::class)
class SubSamplingImageTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val timeout = Timeout.seconds(10)!!
  @get:Rule val testName = TestName()

  private val screenshotValidator = CiScreenshotValidator(
    context = { rule.activity },
    tolerancePercentOnLocal = 0f,
    tolerancePercentOnCi = 0.01f,
  )
  @get:Rule val dropshots = Dropshots(
    filenameFunc = { it },
    resultValidator = screenshotValidator,
  )

  @Before
  fun setup() {
    rule.activityRule.scenario.onActivity {
      it.prepareForScreenshotTest()
    }
  }

  @Test fun various_image_sources(
    @TestParameter imageSource: ImageSourceParam
  ) {
    var isImageDisplayed = false

    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      )
      val context = LocalContext.current
      val imageState = rememberSubSamplingImageState(
        zoomableState = zoomableState,
        imageSource = remember { imageSource.source(context) }
      )
      LaunchedEffect(imageState.isImageLoadedInFullQuality) {
        isImageDisplayed = imageState.isImageLoadedInFullQuality
      }

      SubSamplingImage(
        modifier = Modifier
          .fillMaxSize()
          .zoomable(zoomableState),
        state = imageState,
        contentDescription = null,
      )
    }

    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23())
    }
  }

  @Test fun various_image_sizes_and_layout_sizes(
    @TestParameter layoutSize: LayoutSizeParam,
    @TestParameter imageSize: ImageSizeParam,
  ) {
    var isImageDisplayed = false

    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      )
      val imageState = rememberSubSamplingImageState(
        zoomableState = zoomableState,
        imageSource = imageSize.source,
      )
      LaunchedEffect(imageState.isImageLoadedInFullQuality) {
        isImageDisplayed = imageState.isImageLoadedInFullQuality
      }

      SubSamplingImage(
        modifier = layoutSize.modifier
          .zoomable(zoomableState)
          .border(1.dp, Color.Yellow),
        state = imageState,
        contentDescription = null,
      )
    }

    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23())
    }
  }

  @Test fun various_content_alignments(
    @TestParameter alignment: AlignmentParam,
    @TestParameter size: LayoutSizeParam,
  ) {
    var isImageDisplayed = false
    var tiles: List<CanvasRegionTile> = emptyList()

    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      ).also {
        it.contentAlignment = alignment.value
      }
      val imageState = rememberSubSamplingImageState(
        zoomableState = zoomableState,
        imageSource = SubSamplingImageSource.asset("pahade.jpg"),
      )
      LaunchedEffect(imageState.isImageLoadedInFullQuality) {
        isImageDisplayed = imageState.isImageLoadedInFullQuality
      }
      LaunchedEffect(imageState.tiles) {
        tiles = imageState.tiles
      }

      SubSamplingImage(
        modifier = Modifier
          .then(size.modifier)
          .zoomable(zoomableState)
          .testTag("image"),
        state = imageState,
        contentDescription = null,
      )
    }

    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23())
    }

    rule.onNodeWithTag("image").performTouchInput {
      doubleClick()
    }
    rule.runOnIdle {
      // Wait for full-resolution tiles to load.
      rule.waitUntil(5.seconds) { tiles.all { it.bitmap != null } }
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23(), name = testName.methodName + "_zoomed")
    }
  }

  @Test fun updating_of_image_works() {
    var isImageDisplayed = false
    var imageSource by mutableStateOf(SubSamplingImageSource.asset("smol.jpg"))

    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      )
      val imageState = rememberSubSamplingImageState(
        zoomableState = zoomableState,
        imageSource = imageSource,
      )
      LaunchedEffect(imageState.isImageLoadedInFullQuality) {
        isImageDisplayed = imageState.isImageLoadedInFullQuality
      }

      SubSamplingImage(
        modifier = Modifier
          .fillMaxSize()
          .zoomable(zoomableState),
        state = imageState,
        contentDescription = null,
      )
    }
    rule.waitUntil(5.seconds) { isImageDisplayed }

    imageSource = SubSamplingImageSource.asset("path.jpg")

    rule.waitUntil { !isImageDisplayed }
    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23())
    }
  }

  // todo.
  @Test fun updating_of_image_works_when_content_transformation_was_non_empty() {
  }

  @Test fun draw_base_tile_to_fill_gaps_in_foreground_tiles() {
    screenshotValidator.tolerancePercentOnCi = 0.12f

    // This test blocks 2 decoders indefinitely so at least 3 decoders are needed
    PooledImageRegionDecoder.overriddenPoolCount = 3

    // This fake factory will ignore decoding of selected tiles.
    val shouldIgnore: (BitmapRegionTile) -> Boolean = { region ->
      region.sampleSize == BitmapSampleSize(1) && region.bounds.left == 3648
    }
    val fakeRegionDecoderFactory = ImageRegionDecoder.Factory { context, imageSource, imageOptions, exif ->
      val real = AndroidImageRegionDecoder.Factory.create(context, imageSource, imageOptions, exif)
      object : ImageRegionDecoder by real {
        override suspend fun decodeRegion(region: BitmapRegionTile): ImageBitmap {
          return if (shouldIgnore(region)) {
            delay(Long.MAX_VALUE)
            error("shouldn't reach here")
          } else {
            real.decodeRegion(region)
          }
        }
      }
    }

    var imageTiles: List<CanvasRegionTile>? = null

    rule.setContent {
      BoxWithConstraints {
        check(constraints.maxWidth == 1080 && constraints.maxHeight == 2400) {
          "This test was written for a 1080x2400 display."
        }
        CompositionLocalProvider(LocalImageRegionDecoderFactory provides fakeRegionDecoderFactory) {
          val zoomableState = rememberZoomableState(
            zoomSpec = ZoomSpec(maxZoomFactor = 1f)
          ).also {
            it.contentScale = ContentScale.Crop
          }
          val imageState = rememberSubSamplingImageState(
            zoomableState = zoomableState,
            imageSource = SubSamplingImageSource.asset("pahade.jpg"),
          ).also {
            it.showTileBounds = true
          }
          LaunchedEffect(imageState.tiles) {
            imageTiles = imageState.tiles
          }
          SubSamplingImage(
            modifier = Modifier
              .fillMaxSize()
              .zoomable(zoomableState)
              .testTag("image"),
            state = imageState,
            contentDescription = null,
          )
        }
      }
    }

    rule.waitUntil(5.seconds) {
      imageTiles!!.count { !it.isBaseTile && it.bitmap != null } == 2
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23())
    }
  }

  @Test fun up_scaled_tiles_should_not_have_gaps_due_to_precision_loss() {
    screenshotValidator.tolerancePercentOnCi = 0.014f

    var isImageDisplayed = false
    var imageTiles: List<CanvasRegionTile>? = null

    rule.setContent {
      BoxWithConstraints {
        check(constraints.maxWidth == 1080 && constraints.maxHeight == 2400) {
          "This test was written for a 1080x2400 display."
        }

        val imageState = rememberSubSamplingImageState(
          imageSource = SubSamplingImageSource.asset("path.jpg"),
          transformation = ZoomableContentTransformation(
            isSpecified = true,
            contentSize = Size.Unspecified,
            scale = ScaleFactor(scaleX = 0.5949996f, scaleY = 0.5949996f),
            rotationZ = 0f,
            offset = Offset(x = -1041.2019f, y = -10.483643f),
            transformOrigin = TransformOrigin(0f, 0f),
          ),
        )
        LaunchedEffect(imageState.isImageLoadedInFullQuality) {
          isImageDisplayed = imageState.isImageLoadedInFullQuality
        }
        LaunchedEffect(imageState.tiles) {
          imageTiles = imageState.tiles
        }

        SubSamplingImage(
          modifier = Modifier.fillMaxSize(),
          state = imageState,
          contentDescription = null,
        )
      }
    }

    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23())

      assertThat(imageTiles!!.map { it.bounds }).containsExactly(
        IntRect(-224, -10, 592, 703),
        IntRect(-224, 703, 592, 1417),
        IntRect(-224, 1417, 592, 2169),
        IntRect(592, -10, 1409, 703),
        IntRect(592, 703, 1409, 1417),
        IntRect(592, 1417, 1409, 2169),
      )
    }
  }

  @Test fun center_aligned_and_wrap_content() {
    var isImageDisplayed = false

    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      )
      val imageState = rememberSubSamplingImageState(
        zoomableState = zoomableState,
        imageSource = SubSamplingImageSource.asset("smol.jpg"),
      )
      LaunchedEffect(imageState.isImageLoadedInFullQuality) {
        isImageDisplayed = imageState.isImageLoadedInFullQuality
      }

      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        SubSamplingImage(
          modifier = Modifier
            .wrapContentSize()
            .zoomable(zoomableState),
          state = imageState,
          contentDescription = null,
        )
      }
    }

    rule.waitUntil(5.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23())
    }
  }

  @Test fun bitmap_tiles_should_be_at_least_half_of_layout_size(
    @TestParameter size: LayoutSizeParam,
  ) {
    screenshotValidator.tolerancePercentOnCi = 0.1f

    var isImageDisplayedInFullQuality = false

    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      )
      val imageState = rememberSubSamplingImageState(
        zoomableState = zoomableState,
        imageSource = SubSamplingImageSource.asset("pahade.jpg"),
      ).also {
        it.showTileBounds = true
      }
      LaunchedEffect(imageState.isImageLoadedInFullQuality) {
        isImageDisplayedInFullQuality = imageState.isImageLoadedInFullQuality
      }

      SubSamplingImage(
        modifier = Modifier
          .then(size.modifier)
          .zoomable(zoomableState)
          .testTag("image"),
        state = imageState,
        contentDescription = null,
      )
    }

    rule.waitUntil(5.seconds) { isImageDisplayedInFullQuality }
    rule.onNodeWithTag("image").performTouchInput {
      pinch(
        start0 = center,
        start1 = center,
        end0 = center - Offset(0f, 30f),
        end1 = center + Offset(0f, 30f),
      )
    }
    rule.waitUntil(5.seconds) { isImageDisplayedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23())
    }
  }

  @Test fun various_image_orientations_in_exif_metadata(
    @TestParameter imageAsset: ExifRotatedImageAssetParam,
    @TestParameter alignment: AlignmentParam,
    @TestParameter contentScale: ContentScaleParam,
  ) {
    screenshotValidator.tolerancePercentOnCi = 0.06f

    val skipAlignment = when (alignment) {
      AlignmentParam.TopCenter,
      AlignmentParam.Center -> false
      AlignmentParam.BottomCenter -> true
    }
    if (skipAlignment) {
      throw AssumptionViolatedException("Skipping $alignment")
    }

    lateinit var state: SubSamplingImageState

    rule.setContent {
      val zoomableState = rememberZoomableState(ZoomSpec(maxZoomFactor = 2.5f)).also {
        it.contentScale = contentScale.value
        it.contentAlignment = alignment.value
      }

      SubSamplingImage(
        modifier = Modifier
          .fillMaxSize()
          .zoomable(zoomableState)
          .testTag("image"),
        state = rememberSubSamplingImageState(
          zoomableState = zoomableState,
          imageSource = SubSamplingImageSource.asset(imageAsset.assetName),
        ).also { state = it },
        contentDescription = null,
      )
    }

    rule.waitUntil(5.seconds) { state.isImageLoadedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23(), testName.methodName)
    }

    rule.onNodeWithTag("image").performTouchInput {
      doubleClick(position = centerLeft)
    }
    rule.waitUntil(5.seconds) { state.isImageLoadedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23(), testName.methodName + "_zoomed")
    }

    with(rule.onNodeWithTag("image")) {
      performTouchInput {
        swipeLeft(startX = centerRight.x, endX = centerLeft.x)
      }
    }
    rule.waitUntil(5.seconds) { state.isImageLoadedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23(), testName.methodName + "_zoomed_panned")
    }
  }

  @Test fun preview_bitmap_should_not_be_rotated() {
    val previewBitmapMutex = Mutex(locked = true)
    lateinit var state: SubSamplingImageState

    val gatedDecoderFactory = ImageRegionDecoder.Factory { context, imageSource, imageOptions, exif ->
      val real = AndroidImageRegionDecoder.Factory.create(context, imageSource, imageOptions, exif)
      object : ImageRegionDecoder by real {
        override suspend fun decodeRegion(region: BitmapRegionTile): ImageBitmap {
          return previewBitmapMutex.withLock {
            real.decodeRegion(region)
          }
        }
      }
    }

    val previewBitmap = BitmapFactory.decodeStream(
      rule.activity.assets.open("smol.jpg")
    ).asImageBitmap()

    rule.setContent {
      CompositionLocalProvider(LocalImageRegionDecoderFactory provides gatedDecoderFactory) {
        val zoomableState = rememberZoomableState()
        SubSamplingImage(
          modifier = Modifier
            .fillMaxSize()
            .zoomable(zoomableState)
            .testTag("image"),
          state = rememberSubSamplingImageState(
            zoomableState = zoomableState,
            imageSource = SubSamplingImageSource.asset("bellagio_rotated_by_90.jpg", preview = previewBitmap),
          ).also {
            state = it
          },
          contentDescription = null,
        )
      }
    }

    rule.waitUntil(5.seconds) { state.isImageLoaded }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23(), name = testName.methodName + "_preview")
    }

    previewBitmapMutex.unlock()
    rule.waitUntil(5.seconds) { state.isImageLoadedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity.screenshotForMinSdk23(), name = testName.methodName + "_full_quality")
    }
  }

  @Suppress("unused")
  enum class LayoutSizeParam(val modifier: Modifier) {
    FillMaxSize(Modifier.fillMaxSize()),
    WrapContent(Modifier.wrapContentSize()),
  }

  @Suppress("unused")
  enum class AlignmentParam(val value: Alignment) {
    TopCenter(Alignment.TopCenter),
    Center(Alignment.Center),
    BottomCenter(Alignment.BottomCenter),
  }

  @Suppress("unused")
  enum class ContentScaleParam(val value: ContentScale) {
    Fit(ContentScale.Fit),
    Inside(ContentScale.Inside),
    Fill(ContentScale.FillBounds),
  }

  @Suppress("unused")
  enum class ImageSourceParam(val source: Context.() -> SubSamplingImageSource) {
    Asset({ SubSamplingImageSource.asset("pahade.jpg") }),
    Resource({ SubSamplingImageSource.resource(R.drawable.cat_1920) }),
    ContentUri({ SubSamplingImageSource.contentUri(Uri.parse("""android.resource://${packageName}/${R.drawable.cat_1920}""")) }),
    File({ SubSamplingImageSource.file(createFileFromAsset("pahade.jpg")) }),
    RawStream({ SubSamplingImageSource.rawSource({ assets.open("path.jpg").source() }) }),
  }

  @Suppress("unused")
  enum class ExifRotatedImageAssetParam(val assetName: String) {
    RotatedBy90("bellagio_rotated_by_90.jpg"),
    RotatedBy180("bellagio_rotated_by_180.jpg"),
    RotatedBy270("bellagio_rotated_by_270.jpg"),
  }

  @Suppress("unused")
  enum class ImageSizeParam(val source: SubSamplingImageSource) {
    LargeLandscapeImage(SubSamplingImageSource.asset("pahade.jpg")),
    LargePortraitImage(SubSamplingImageSource.resource(R.drawable.cat_1920)),
    SmallSquareImage(SubSamplingImageSource.asset("smol.jpg")),
  }
}

private fun Context.createFileFromAsset(assetName: String): Path {
  return (cacheDir.toOkioPath() / assetName).also { path ->
    FileSystem.SYSTEM.run {
      delete(path)
      write(path) { writeAll(assets.open(assetName).source()) }
    }
  }
}
