package me.saket.telephoto.subsampling

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.WindowManager
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.dropbox.dropshots.Dropshots
import com.dropbox.dropshots.ResultValidator
import com.dropbox.dropshots.ThresholdValidator
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.delay
import leakcanary.DetectLeaksAfterTestSuccess.Companion.detectLeaksAfterTestSuccessWrapping
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.internal.BitmapRegionTile
import me.saket.telephoto.subsamplingimage.internal.BitmapSampleSize
import me.saket.telephoto.subsamplingimage.internal.CanvasRegionTile
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.LocalImageRegionDecoderFactory
import me.saket.telephoto.subsamplingimage.internal.SkiaImageRegionDecoders
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.subsamplingimage.test.R
import me.saket.telephoto.zoomable.ZoomableContentTransformation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.source
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.runner.RunWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(TestParameterInjector::class)
class SubSamplingImageTest {
  private val rule = createAndroidComposeRule<ComponentActivity>()
  private val dropshots = Dropshots(
    filenameFunc = { it },
    resultValidator = ThresholdValidator(thresholdPercent = 0.02f)
  )

  @get:Rule val rules: RuleChain = RuleChain.emptyRuleChain()
    .around(dropshots)
    .detectLeaksAfterTestSuccessWrapping("ActivitiesDestroyed") {
      around(rule)
    }

  @get:Rule val testName = TestName()

  @Before
  fun setup() {
    rule.activityRule.scenario.onActivity {
      it.actionBar?.hide()
      it.window.setBackgroundDrawable(ColorDrawable(0xFF1C1A25.toInt()))

      // Remove any space occupied by system bars to reduce differences
      // in from screenshots generated on different devices.
      it.window.setDecorFitsSystemWindows(false)
      it.window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }
  }

  @Test fun various_image_sources(
    @TestParameter imageSource: ImageSourceParam
  ) {
    var isImageDisplayed = false

    rule.setContent {
      val zoomableState = rememberZoomableState(maxZoomFactor = 1f)
      val context = LocalContext.current
      val imageState = rememberSubSamplingImageState(
        zoomableState = zoomableState,
        imageSource = remember { imageSource.source(context) }
      )
      LaunchedEffect(imageState.isImageDisplayedInFullQuality) {
        isImageDisplayed = imageState.isImageDisplayedInFullQuality
      }

      SubSamplingImage(
        modifier = Modifier
          .fillMaxSize()
          .zoomable(zoomableState),
        state = imageState,
        contentDescription = null,
      )
    }

    rule.waitUntil(2.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun various_image_sizes_and_layout_sizes(
    @TestParameter layoutSize: LayoutSizeParam,
    @TestParameter imageSize: ImageSizeParam,
  ) {
    var isImageDisplayed = false

    rule.setContent {
      val zoomableState = rememberZoomableState(maxZoomFactor = 1f)
      val imageState = rememberSubSamplingImageState(
        zoomableState = zoomableState,
        imageSource = imageSize.source,
      )
      LaunchedEffect(imageState.isImageDisplayedInFullQuality) {
        isImageDisplayed = imageState.isImageDisplayedInFullQuality
      }

      SubSamplingImage(
        modifier = layoutSize.modifier
          .zoomable(zoomableState)
          .border(1.dp, Color.Yellow),
        state = imageState,
        contentDescription = null,
      )
    }

    rule.waitUntil(2.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun various_content_alignments(
    @TestParameter alignment: AlignmentParam,
    @TestParameter size: LayoutSizeParam,
  ) {
    var isImageDisplayed = false
    var tiles: List<CanvasRegionTile> = emptyList()

    rule.setContent {
      val zoomableState = rememberZoomableState(maxZoomFactor = 1f).also {
        it.contentAlignment = alignment.value
      }
      val imageState = rememberSubSamplingImageState(
        zoomableState = zoomableState,
        imageSource = SubSamplingImageSource.asset("pahade.jpg"),
      )
      LaunchedEffect(imageState.isImageDisplayedInFullQuality) {
        isImageDisplayed = imageState.isImageDisplayedInFullQuality
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

    rule.waitUntil(2.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }

    rule.onNodeWithTag("image").performTouchInput {
      doubleClick()
    }
    rule.runOnIdle {
      // Wait for full-resolution tiles to load.
      rule.waitUntil { tiles.all { it.bitmap != null } }
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, name = testName.methodName + "_zoomed")
    }
  }

  @Test fun updating_of_image_works() {
    var isImageDisplayed = false
    var imageSource by mutableStateOf(SubSamplingImageSource.asset("smol.jpg"))

    rule.setContent {
      val zoomableState = rememberZoomableState(maxZoomFactor = 1f)
      val imageState = rememberSubSamplingImageState(
        zoomableState = zoomableState,
        imageSource = imageSource,
      )
      LaunchedEffect(imageState.isImageDisplayedInFullQuality) {
        isImageDisplayed = imageState.isImageDisplayedInFullQuality
      }

      SubSamplingImage(
        modifier = Modifier
          .fillMaxSize()
          .zoomable(zoomableState),
        state = imageState,
        contentDescription = null,
      )
    }
    rule.waitUntil(2.seconds) { isImageDisplayed }

    imageSource = SubSamplingImageSource.asset("path.jpg")

    rule.waitUntil { !isImageDisplayed }
    rule.waitUntil { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  // todo.
  @Test fun updating_of_image_works_when_content_transformation_was_non_empty() {
  }

  @Test fun draw_base_tile_to_fill_gaps_in_foreground_tiles() {
    // This fake factory will ignore decoding of selected tiles.
    val shouldIgnore: (BitmapRegionTile) -> Boolean = { region ->
      region.sampleSize == BitmapSampleSize(1) && region.bounds.left == 3648
    }
    val fakeRegionDecoderFactory = ImageRegionDecoder.Factory { context, imageSource, bitmapConfig ->
      val real = SkiaImageRegionDecoders.Factory.create(context, imageSource, bitmapConfig)
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

    var isImageDisplayed = false

    rule.setContent {
      CompositionLocalProvider(LocalImageRegionDecoderFactory provides fakeRegionDecoderFactory) {
        val zoomableState = rememberZoomableState(maxZoomFactor = 1f)
        val imageState = rememberSubSamplingImageState(
          zoomableState = zoomableState,
          imageSource = SubSamplingImageSource.asset("pahade.jpg"),
        ).also {
          it.showTileBounds = true
        }
        LaunchedEffect(imageState.isImageDisplayed) {
          isImageDisplayed = imageState.isImageDisplayed
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

    rule.waitUntil(2.seconds) { isImageDisplayed }
    rule.onNodeWithTag("image").performTouchInput { doubleClick(center) }

    rule.runOnIdle {
      // Wait for white-listed tiles to load full-resolution bitmaps.
      Thread.sleep(200)
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun up_scaled_tiles_should_not_have_gaps_due_to_precision_loss() {
    var isImageDisplayed = false
    var imageTiles: List<CanvasRegionTile>? = null

    rule.setContent {
      BoxWithConstraints {
        val imageState = rememberSubSamplingImageState(
          imageSource = SubSamplingImageSource.asset("path.jpg"),
          transformation = ZoomableContentTransformation(
            scale = ScaleFactor(scaleX = 1.1845919f, scaleY = 1.1845919f),
            offset = Offset(x = -2749.3718f, y = -1045.4058f),
            rotationZ = 0f,
            transformOrigin = TransformOrigin(0f, 0f)
          ),
        )
        LaunchedEffect(imageState.isImageDisplayedInFullQuality) {
          isImageDisplayed = imageState.isImageDisplayedInFullQuality
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

    rule.waitUntil(2.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)

      assertThat(imageTiles!!.map { it.bounds }).containsExactly(
        IntRect(-1122, -1045, 503, 340),
        IntRect(-1122, 340, 503, 1726),
        IntRect(-1122, 1726, 503, 3293),
        IntRect(503, -1045, 2129, 340),
        IntRect(503, 340, 2129, 1726),
        IntRect(503, 1726, 2129, 3293),
      )
    }
  }

  @Test fun center_aligned_and_wrap_content() {
    var isImageDisplayed = false

    rule.setContent {
      val zoomableState = rememberZoomableState(maxZoomFactor = 1f)
      val imageState = rememberSubSamplingImageState(
        zoomableState = zoomableState,
        imageSource = SubSamplingImageSource.asset("smol.jpg"),
      )
      LaunchedEffect(imageState.isImageDisplayedInFullQuality) {
        isImageDisplayed = imageState.isImageDisplayedInFullQuality
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

    rule.waitUntil(2.seconds) { isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun bitmap_tiles_should_be_at_least_half_of_layout_size(
    @TestParameter size: LayoutSizeParam,
  ) {
    var isImageDisplayedInFullQuality = false

    rule.setContent {
      val zoomableState = rememberZoomableState(maxZoomFactor = 1f)
      val imageState = rememberSubSamplingImageState(
        zoomableState = zoomableState,
        imageSource = SubSamplingImageSource.asset("pahade.jpg"),
      ).also {
        it.showTileBounds = true
      }
      LaunchedEffect(imageState.isImageDisplayedInFullQuality) {
        isImageDisplayedInFullQuality = imageState.isImageDisplayedInFullQuality
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

    rule.waitUntil { isImageDisplayedInFullQuality }
    rule.onNodeWithTag("image").performTouchInput {
      pinch(
        start0 = center,
        start1 = center,
        end0 = center - Offset(0f, 30f),
        end1 = center + Offset(0f, 30f),
      )
    }
    rule.waitUntil { isImageDisplayedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
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
  enum class ImageSourceParam(val source: Context.() -> SubSamplingImageSource) {
    Asset({ SubSamplingImageSource.asset("pahade.jpg") }),
    Resource({ SubSamplingImageSource.resource(R.drawable.cat_1920) }),
    ContentUri({ SubSamplingImageSource.contentUri(Uri.parse("""android.resource://${packageName}/${R.drawable.cat_1920}""")) }),
    File({ SubSamplingImageSource.file(createFileFromAsset("pahade.jpg")) })
  }

  @Suppress("unused")
  enum class ImageSizeParam(val source: SubSamplingImageSource) {
    LargeLandscapeImage(SubSamplingImageSource.asset("pahade.jpg")),
    LargePortraitImage(SubSamplingImageSource.resource(R.drawable.cat_1920)),
    SmallSquareImage(SubSamplingImageSource.asset("smol.jpg")),
  }
}

private fun ThresholdValidator(thresholdPercent: Float): ResultValidator =
  ThresholdValidator(threshold = thresholdPercent / 100)

private fun AndroidComposeTestRule<*, *>.waitUntil(timeout: Duration, condition: () -> Boolean) {
  this.waitUntil(timeoutMillis = timeout.inWholeMilliseconds, condition)
}

private fun Context.createFileFromAsset(assetName: String): Path {
  return (cacheDir.toOkioPath() / assetName).also { path ->
    FileSystem.SYSTEM.run {
      delete(path)
      write(path) { writeAll(assets.open(assetName).source()) }
    }
  }
}
