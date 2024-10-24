@file:Suppress("NAME_SHADOWING")
@file:OptIn(ExperimentalCoroutinesApi::class)

package me.saket.telephoto.subsamplingimage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import me.saket.telephoto.subsamplingimage.internal.LocalImageRegionDecoderFactory
import me.saket.telephoto.subsamplingimage.internal.PooledImageRegionDecoder
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomableContentTransformation
import me.saket.telephoto.zoomable.ZoomableState
import java.io.IOException

/**
 * Create a [SubSamplingImageState] that can be used with [SubSamplingImage] which uses
 * [Modifier.zoomable][me.saket.telephoto.zoomable.zoomable] as its gesture detector.
 *
 * ```kotlin
 * val zoomableState = rememberZoomableState()
 * val imageState = rememberSubSamplingImageState(
 *   zoomableState = zoomableState,
 *   imageSource = SubSamplingImageSource.asset("fox.jpg")
 * )
 *
 * SubSamplingImage(
 *   modifier = Modifier
 *     .fillMaxSize()
 *     .zoomable(zoomableState),
 *   state = imageState,
 *   contentDescription = …,
 * )
 * ```
 */
@Composable
fun rememberSubSamplingImageState(
  imageSource: SubSamplingImageSource,
  zoomableState: ZoomableState,
  imageOptions: ImageBitmapOptions = ImageBitmapOptions.Default,
  errorReporter: SubSamplingImageErrorReporter = SubSamplingImageErrorReporter.NoOpInRelease
): SubSamplingImageState {
  val state = rememberSubSamplingImageState(
    imageSource = imageSource,
    transformation = { zoomableState.contentTransformation },
    imageOptions = imageOptions,
    errorReporter = errorReporter,
  )

  // SubSamplingImage will apply the transformations on its own.
  DisposableEffect(state) {
    val previousValue = zoomableState.autoApplyTransformations
    zoomableState.autoApplyTransformations = false
    onDispose {
      zoomableState.autoApplyTransformations = previousValue
    }
  }

  zoomableState.setContentLocation(
    ZoomableContentLocation.unscaledAndTopLeftAligned(state.imageSize?.toSize())
  )
  return state
}

@Composable
internal fun rememberSubSamplingImageState(
  imageSource: SubSamplingImageSource,
  transformation: () -> ZoomableContentTransformation,
  imageOptions: ImageBitmapOptions = ImageBitmapOptions.Default,
  errorReporter: SubSamplingImageErrorReporter = SubSamplingImageErrorReporter.NoOpInRelease,
): SubSamplingImageState {
  DisposableEffect(imageSource) {
    onDispose {
      imageSource.close()
    }
  }

  val decoder: ImageRegionDecoder? = createRegionDecoder(imageSource, imageOptions, errorReporter)
  return remember(imageSource) {
    RealSubSamplingImageState(imageSource)
  }.also {
    it.contentTransformation = transformation
    it.imageSize = decoder?.imageSize
    it.imageRegionDecoder = decoder
  }
}

@Composable
private fun createRegionDecoder(
  imageSource: SubSamplingImageSource,
  imageOptions: ImageBitmapOptions,
  errorReporter: SubSamplingImageErrorReporter
): ImageRegionDecoder? {
  val context = LocalContext.current
  val errorReporter by rememberUpdatedState(errorReporter)
  var decoder by remember(imageSource) { mutableStateOf<ImageRegionDecoder?>(null) }

  if (!LocalInspectionMode.current) {
    val factory = PooledImageRegionDecoder.Factory(LocalImageRegionDecoderFactory.current)
    LaunchedEffect(imageSource) {
      try {
        val exif = ExifMetadata.read(context, imageSource)
        decoder = factory.create(
          ImageRegionDecoder.FactoryParams(
            context = context,
            imageSource = imageSource,
            imageOptions = imageOptions,
            exif = exif,
          )
        )
      } catch (e: IOException) {
        errorReporter.onImageLoadingFailed(e, imageSource)
      }
    }
    DisposableEffect(imageSource) {
      onDispose {
        decoder?.close()
        decoder = null
      }
    }
  }

  return decoder
}

/** State for [SubSamplingImage]. */
@Stable
sealed interface SubSamplingImageState {
  val imageSize: IntSize?

  /**
   * Whether all the visible tiles have been loaded and the image is displayed (not necessarily in its full quality).
   *
   * Also see [isImageLoadedInFullQuality].
   */
  val isImageLoaded: Boolean

  /** Whether all the visible and *full resolution* tiles have been loaded and the image is displayed. */
  val isImageLoadedInFullQuality: Boolean
}
