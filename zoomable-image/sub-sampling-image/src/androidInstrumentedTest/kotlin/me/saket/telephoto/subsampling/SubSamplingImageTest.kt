@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.subsampling

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.dropbox.dropshots.Dropshots
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import leakcanary.LeakAssertions
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.RealSubSamplingImageState
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImageState
import me.saket.telephoto.subsamplingimage.internal.AndroidImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.ImageRegionTile
import me.saket.telephoto.subsamplingimage.internal.ImageSampleSize
import me.saket.telephoto.subsamplingimage.internal.LocalImageRegionDecoderFactory
import me.saket.telephoto.subsamplingimage.internal.PooledImageRegionDecoder
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.subsamplingimage.test.R
import me.saket.telephoto.util.CiScreenshotValidator
import me.saket.telephoto.util.ScreenshotTestActivity
import me.saket.telephoto.util.waitUntil
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.internal.RealZoomableContentTransformation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.source
import org.junit.After
import org.junit.AssumptionViolatedException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@RunWith(TestParameterInjector::class)
class SubSamplingImageTest {
  @get:Rule val rule = createAndroidComposeRule<ScreenshotTestActivity>()
  @get:Rule val timeout = Timeout.seconds(30)!!
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

  @After
  fun tearDown() {
    PooledImageRegionDecoder.overriddenPoolCount = null
    LeakAssertions.assertNoLeaks()
  }

  @Test fun various_image_sources(
    @TestParameter imageSource: ImageSourceParam
  ) {
    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      )
      val context = LocalContext.current
      SubSamplingImage(
        modifier = Modifier
          .fillMaxSize()
          .zoomable(zoomableState)
          .testTag("image"),
        state = rememberSubSamplingImageState(
          zoomableState = zoomableState,
          imageSource = remember { imageSource.source(context) }
        ),
        contentDescription = null,
      )
    }

    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun various_image_sizes_and_layout_sizes(
    @TestParameter layoutSize: LayoutSizeParam,
    @TestParameter imageSize: ImageSizeParam,
  ) {
    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      )
      SubSamplingImage(
        modifier = layoutSize.modifier
          .zoomable(zoomableState)
          .border(1.dp, Color.Yellow)
          .testTag("image"),
        state = rememberSubSamplingImageState(
          zoomableState = zoomableState,
          imageSource = imageSize.source,
        ),
        contentDescription = null,
      )
    }

    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun various_content_alignments(
    @TestParameter alignment: AlignmentParam,
    @TestParameter size: LayoutSizeParam,
  ) {
    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      ).also {
        it.contentAlignment = alignment.value
      }
      SubSamplingImage(
        modifier = Modifier
          .then(size.modifier)
          .zoomable(zoomableState)
          .testTag("image"),
        state = rememberSubSamplingImageState(
          zoomableState = zoomableState,
          imageSource = SubSamplingImageSource.asset("pahade.jpg"),
        ),
        contentDescription = null,
      )
    }

    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }

    rule.onNodeWithTag("image").performTouchInput {
      doubleClick()
    }

    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, name = testName.methodName + "_zoomed")
    }
  }

  @Test fun updating_of_image_works() {
    var imageSource by mutableStateOf(SubSamplingImageSource.asset("smol.jpg"))

    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      )
      val imageState = rememberSubSamplingImageState(
        zoomableState = zoomableState,
        imageSource = imageSource,
      )

      SubSamplingImage(
        modifier = Modifier
          .fillMaxSize()
          .zoomable(zoomableState)
          .testTag("image"),
        state = imageState,
        contentDescription = null,
      )
    }
    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }

    imageSource = SubSamplingImageSource.asset("path.jpg")

    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  // todo.
  @Test fun updating_of_image_works_when_content_transformation_was_non_empty() {
  }

  @Test fun draw_base_tile_to_fill_gaps_in_foreground_tiles() {
    // This test blocks 2 decoders indefinitely so at least 3 decoders are needed.
    PooledImageRegionDecoder.overriddenPoolCount = 3

    // This fake image factory will only decode the base tile.
    val fakeRegionDecoderFactory = ImageRegionDecoder.Factory { params ->
      val real = AndroidImageRegionDecoder.Factory.create(params)
      object : ImageRegionDecoder by real {
        override suspend fun decodeRegion(region: ImageRegionTile): ImageRegionDecoder.DecodeResult {
          return if (region.sampleSize == ImageSampleSize(1) && region.bounds.left == 3648) {
            delay(Long.MAX_VALUE)
            error("shouldn't reach here")
          } else {
            real.decodeRegion(region)
          }
        }
      }
    }

    rule.setContent {
      BoxWithConstraints {
        check(constraints.maxWidth == 1080 && constraints.maxHeight == 2400) {
          "This test was written for a 1080x2400 display. Current size = $constraints"
        }
        CompositionLocalProvider(LocalImageRegionDecoderFactory provides fakeRegionDecoderFactory) {
          val zoomableState = rememberZoomableState(
            zoomSpec = ZoomSpec(maxZoomFactor = 1f)
          ).also {
            it.contentScale = ContentScale.Crop
          }

          SubSamplingImage(
            modifier = Modifier
              .fillMaxSize()
              .zoomable(zoomableState)
              .testTag("image"),
            state = rememberSubSamplingImageState(
              zoomableState = zoomableState,
              imageSource = SubSamplingImageSource.asset("pahade.jpg"),
            ).asReal().also {
              it.showTileBounds = true
            },
            contentDescription = null,
          )
        }
      }
    }

    rule.waitUntil(5.seconds) {
      val tiles = rule.onNodeWithTag("image").viewportImageTiles()
      tiles != null && tiles.count { !it.isBase && it.painter != null } == 2
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun draw_tile_under_centroid_first() {
    // This test only allows 1 decoder to work so at least 2 decoders are needed.
    PooledImageRegionDecoder.overriddenPoolCount = 2

    // This fake factory will ignore decoding of all but the first tiles.
    val firstNonBaseTileReceived = AtomicBoolean(false)
    val fakeRegionDecoderFactory = ImageRegionDecoder.Factory { params ->
      val real = AndroidImageRegionDecoder.Factory.create(params)
      object : ImageRegionDecoder by real {
        override suspend fun decodeRegion(region: ImageRegionTile): ImageRegionDecoder.DecodeResult {
          val isBaseTile = region.sampleSize.size == 8
          val isCentroidTile = region.sampleSize.size == 1 && region.bounds == IntRect(0, 1200, 1216, 3265)
          return if (isBaseTile || (isCentroidTile && !firstNonBaseTileReceived.getAndSet(true))) {
            real.decodeRegion(region)
          } else {
            delay(Long.MAX_VALUE)
            error("shouldn't reach here")
          }
        }
      }
    }

    rule.setContent {
      CompositionLocalProvider(LocalImageRegionDecoderFactory provides fakeRegionDecoderFactory) {
        val zoomableState = rememberZoomableState()
        SubSamplingImage(
          modifier = Modifier
            .fillMaxSize()
            .zoomable(zoomableState)
            .testTag("image"),
          state = rememberSubSamplingImageState(
            zoomableState = zoomableState,
            imageSource = SubSamplingImageSource.asset("pahade.jpg"),
          ).asReal().also {
            it.showTileBounds = true
          },
          contentDescription = null,
        )
      }
    }

    rule.onNodeWithTag("image").performTouchInput {
      doubleClick(position = centerLeft + Offset(100f, 0f))
    }
    rule.waitUntil(5.seconds) {
      val tiles = rule.onNodeWithTag("image").viewportImageTiles()
      tiles != null && tiles.count { !it.isBase && it.painter != null } == 1
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun up_scaled_tiles_should_not_have_gaps_due_to_precision_loss() {
    screenshotValidator.tolerancePercentOnCi = 0.013f

    rule.setContent {
      BoxWithConstraints {
        check(constraints.maxWidth == 1080 && constraints.maxHeight == 2400) {
          "This test was written for a 1080x2400 display."
        }

        val imageState = rememberSubSamplingImageState(
          imageSource = SubSamplingImageSource.asset("path.jpg"),
          transformation = {
            RealZoomableContentTransformation(
              isSpecified = true,
              contentSize = Size.Unspecified,
              scale = ScaleFactor(scaleX = 0.5949996f, scaleY = 0.5949996f),
              scaleMetadata = RealZoomableContentTransformation.ScaleMetadata(
                initialScale = ScaleFactor.Unspecified,
                userZoom = 0f,
              ),
              rotationZ = 0f,
              offset = Offset(x = -1041.2019f, y = -10.483643f),
              centroid = Offset.Zero,
            )
          },
        )

        SubSamplingImage(
          modifier = Modifier
            .fillMaxSize()
            .testTag("image"),
          state = imageState,
          contentDescription = null,
        )
      }
    }

    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }

    val imageTiles = rule.onNodeWithTag("image").viewportImageTiles()!!
    assertThat(imageTiles.map { it.bounds }).containsExactly(
      IntRect(-224, -10, 592, 703),
      IntRect(-224, 703, 592, 1417),
      IntRect(-224, 1417, 592, 2169),
      IntRect(592, -10, 1409, 703),
      IntRect(592, 703, 1409, 1417),
      IntRect(592, 1417, 1409, 2169),
    )
  }

  @Test fun center_aligned_and_wrap_content() {
    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 2f)
      )
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        SubSamplingImage(
          modifier = Modifier
            .wrapContentSize()
            .zoomable(zoomableState)
            .testTag("image"),
          state = rememberSubSamplingImageState(
            zoomableState = zoomableState,
            imageSource = SubSamplingImageSource.asset("fox_1000.jpg"),
          ),
          contentDescription = null,
        )
      }
    }

    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }

    rule.onNodeWithTag("image").performTouchInput {
      doubleClick()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, name = "${testName.methodName}_with_zoom")
    }

    rule.onNodeWithTag("image").performTouchInput {
      swipeLeft(startX = centerRight.x, endX = centerLeft.x)
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, name = "${testName.methodName}_with_zoom_and_pan")
    }
  }

  @Test fun bitmap_tiles_should_be_at_least_half_of_layout_size(
    @TestParameter size: LayoutSizeParam,
  ) {
    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      )

      SubSamplingImage(
        modifier = Modifier
          .then(size.modifier)
          .zoomable(zoomableState)
          .testTag("image"),
        state = rememberSubSamplingImageState(
          zoomableState = zoomableState,
          imageSource = SubSamplingImageSource.asset("pahade.jpg"),
        ).asReal().also {
          it.showTileBounds = true
        },
        contentDescription = null,
      )
    }

    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    rule.onNodeWithTag("image").performTouchInput {
      pinch(
        start0 = center,
        start1 = center,
        end0 = center,
        end1 = center + Offset(0f, 1f),
      )
    }
    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun various_image_orientations_in_exif_metadata(
    @TestParameter imageAsset: ExifRotatedImageAssetParam,
    @TestParameter alignment: AlignmentParam,
    @TestParameter contentScale: ContentScaleParam,
  ) {
    screenshotValidator.tolerancePercentOnCi = 0.02f

    val skipAlignment = when (alignment) {
      AlignmentParam.TopCenter,
      AlignmentParam.Center -> false
      AlignmentParam.BottomCenter -> true
    }
    if (skipAlignment) {
      throw AssumptionViolatedException("Skipping $alignment")
    }

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
        ),
        contentDescription = null,
      )
    }

    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }

    rule.onNodeWithTag("image").performTouchInput {
      doubleClick(position = centerLeft)
    }
    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_zoomed")
    }

    rule.onNodeWithTag("image").performTouchInput {
      swipeLeft(startX = centerRight.x, endX = centerLeft.x)
    }
    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_zoomed_panned")
    }
  }

  @Test fun preview_bitmap_should_not_be_rotated() {
    val previewBitmapMutex = Mutex(locked = true)
    var fullImageDecoded = false

    val gatedDecoderFactory = ImageRegionDecoder.Factory { params ->
      val real = AndroidImageRegionDecoder.Factory.create(params)
      object : ImageRegionDecoder by real {
        override suspend fun decodeRegion(region: ImageRegionTile): ImageRegionDecoder.DecodeResult {
          return previewBitmapMutex.withLock {
            real.decodeRegion(region)
          }.also {
            fullImageDecoded = true
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
          ),
          contentDescription = null,
        )
      }
    }

    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayed()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, name = testName.methodName + "_preview")
    }

    previewBitmapMutex.unlock()

    rule.waitUntil { fullImageDecoded }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, name = testName.methodName + "_full_quality")
    }
  }

  @Test fun wrap_content_size_in_a_vertically_infinite_layout() {
    rule.setContent {
      val zoomableState = rememberZoomableState(ZoomSpec(maxZoomFactor = 2.5f))

      Column(
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .border(1.dp, Color.Red)
      ) {
        SubSamplingImage(
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Yellow)
            .zoomable(zoomableState)
            .testTag("image"),
          state = rememberSubSamplingImageState(
            zoomableState = zoomableState,
            imageSource = SubSamplingImageSource.asset("pahade.jpg"),
          ),
          contentDescription = null,
        )
      }
    }

    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun do_not_draw_any_tiles_until_all_of_the_visible_portion_of_the_image_can_be_shown() {
    // todo.
  }

  @Test fun do_not_draw_base_tile_after_foreground_tiles_images_are_loaded() {
    // This test blocks 1 decoders so at least 2 decoders are needed.
    PooledImageRegionDecoder.overriddenPoolCount = 2

    val mutexForDecodingLastTile = Mutex(locked = true)

    val fakeRegionDecoderFactory = ImageRegionDecoder.Factory { params ->
      val real = AndroidImageRegionDecoder.Factory.create(params)
      object : ImageRegionDecoder by real {
        override suspend fun decodeRegion(region: ImageRegionTile): ImageRegionDecoder.DecodeResult {
          return if (region.sampleSize == ImageSampleSize(1)) {
            if (region.bounds.topLeft == IntOffset(4864, 1200)) {
              mutexForDecodingLastTile.lock()
            }
            ImageRegionDecoder.DecodeResult(
              painter = ColorPainter(Color.Yellow.copy(alpha = 0.5f)),
              hasUltraHdrContent = false,
            )
          } else {
            real.decodeRegion(region)
          }
        }
      }
    }

    lateinit var imageState: SubSamplingImageState
    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      )
      CompositionLocalProvider(LocalImageRegionDecoderFactory provides fakeRegionDecoderFactory) {
        imageState = rememberSubSamplingImageState(
          zoomableState = zoomableState,
          imageSource = SubSamplingImageSource.asset("pahade.jpg"),
        )
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

    rule.waitUntil { imageState.isImageDisplayed }
    rule.onNodeWithTag("image").performTouchInput { doubleClick() }
    rule.waitForIdle()

    rule.waitUntil {
      // Wait until all but the delayed tile are loaded.
      imageState.asReal().viewportImageTiles.count { !it.isBase && it.painter != null } == 3
    }
    rule.runOnIdle {
      // The base image should still be visible behind the foreground tiles.
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_[before_loading_all_tiles]")
    }

    mutexForDecodingLastTile.unlock()

    rule.waitUntil { imageState.isImageDisplayedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_[after_loading_all_tiles]")
    }
  }

  @Test fun do_not_load_images_for_tiles_that_are_not_visible() {
    val decodedRegionCount = AtomicInteger(0)
    val recordingDecoderFactory = ImageRegionDecoder.Factory { params ->
      val real = AndroidImageRegionDecoder.Factory.create(params)
      object : ImageRegionDecoder by real {
        override suspend fun decodeRegion(region: ImageRegionTile): ImageRegionDecoder.DecodeResult =
          real.decodeRegion(region).also {
            if (region.sampleSize == ImageSampleSize(1)) {
              decodedRegionCount.incrementAndGet()
            }
          }
      }
    }

    lateinit var imageState: SubSamplingImageState
    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      )
      CompositionLocalProvider(LocalImageRegionDecoderFactory provides recordingDecoderFactory) {
        imageState = rememberSubSamplingImageState(
          zoomableState = zoomableState,
          imageSource = SubSamplingImageSource.asset("pahade.jpg"),
        )
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

    rule.waitUntil { imageState.isImageDisplayed }
    rule.onNodeWithTag("image").performTouchInput { doubleClick() }

    rule.waitUntil(3.seconds) { imageState.isImageDisplayedInFullQuality }
    rule.runOnIdle {
      assertThat(decodedRegionCount.get()).isEqualTo(4)
    }
  }

  // Regression test for https://github.com/saket/telephoto/issues/110
  @Test fun unknown_color_space() {
    val bitmap = rule.activity.assets.open("grayscale.jpg").use {
      BitmapFactory.decodeStream(it)
    }

    rule.setContent {
      val zoomableState = rememberZoomableState()
      SubSamplingImage(
        modifier = Modifier
          .fillMaxSize()
          .zoomable(zoomableState)
          .testTag("image"),
        state = rememberSubSamplingImageState(
          zoomableState = zoomableState,
          imageSource = SubSamplingImageSource.asset("grayscale.jpg"),
          imageOptions = ImageBitmapOptions(from = bitmap),
        ),
        contentDescription = null,
      )
    }

    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun raw_stream_works_with_multiple_decoders() {
    PooledImageRegionDecoder.overriddenPoolCount = 2

    rule.setContent {
      val zoomableState = rememberZoomableState()
      val context = LocalContext.current
      SubSamplingImage(
        modifier = Modifier
          .fillMaxSize()
          .zoomable(zoomableState)
          .testTag("image"),
        state = rememberSubSamplingImageState(
          zoomableState = zoomableState,
          imageSource = remember {
            SubSamplingImageSource.rawSource({ context.assets.open("path.jpg").source() })
          }
        ),
        contentDescription = null,
      )
    }

    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayed()
    }
  }

  @Test fun enable_hdr_color_mode_for_images_that_contain_ultra_hdr_content() {
    assertThat(rule.activity.window.colorMode).isEqualTo(ActivityInfo.COLOR_MODE_DEFAULT)

    var imageAssetName by mutableStateOf("fox_1000.jpg")
    rule.setContent {
      val zoomableState = rememberZoomableState()
      SubSamplingImage(
        modifier = Modifier
          .fillMaxSize()
          .zoomable(zoomableState)
          .testTag("image"),
        state = rememberSubSamplingImageState(
          zoomableState = zoomableState,
          imageSource = SubSamplingImageSource.asset(imageAssetName),
        ),
        contentDescription = null,
      )
    }

    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayed()
    }
    assertThat(rule.activity.window.colorMode).isEqualTo(ActivityInfo.COLOR_MODE_DEFAULT)

    imageAssetName = "vadapav_ultra_hdr.jpg"
    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    assertThat(rule.activity.window.colorMode).isEqualTo(ActivityInfo.COLOR_MODE_HDR)

    imageAssetName = "path.jpg"
    rule.waitUntil {
      rule.onNodeWithTag("image").isImageDisplayedInFullQuality()
    }
    assertThat(rule.activity.window.colorMode).isEqualTo(ActivityInfo.COLOR_MODE_DEFAULT)
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

private fun SubSamplingImageState.asReal() = this as RealSubSamplingImageState
