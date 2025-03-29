@file:Suppress("NAME_SHADOWING")

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
import me.saket.telephoto.subsamplingimage.internal.AndroidImageDecoderFactoryParams
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
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
  DisposableEffect(zoomableState) {
    val previousValue = zoomableState.autoApplyTransformations
    zoomableState.autoApplyTransformations = false
    onDispose {
      zoomableState.autoApplyTransformations = previousValue
    }
  }

  check(state is RealSubSamplingImageState)
  zoomableState.setContentLocation(
    ZoomableContentLocation.unscaledAndTopLeftAligned(state.imageOrPreviewSize?.toSize())
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
  val transformation by rememberUpdatedState(transformation)
  val state = remember(imageSource) {
    RealSubSamplingImageState(imageSource, transformation)
  }.also {
    it.imageRegionDecoder = createImageRegionDecoder(imageSource, imageOptions, errorReporter)
  }

  state.LoadImageTilesEffect()
  DisposableEffect(imageSource) {
    onDispose {
      imageSource.close()
    }
  }
  return state
}

@Composable
private fun createImageRegionDecoder(
  imageSource: SubSamplingImageSource,
  imageOptions: ImageBitmapOptions,
  errorReporter: SubSamplingImageErrorReporter
): ImageRegionDecoder? {
  val errorReporter by rememberUpdatedState(errorReporter)
  var decoder by remember(imageSource) { mutableStateOf<ImageRegionDecoder?>(null) }

  if (!LocalInspectionMode.current) {
    val context = LocalContext.current
    LaunchedEffect(imageSource) {
      try {
        decoder = imageSource.decoder().create(
          AndroidImageDecoderFactoryParams(
            context = context,
            imageOptions = imageOptions,
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
  /** Raw size of the image, without any scaling applied. */
  val imageSize: IntSize?

  /**
   * Whether the image is loaded and displayed, but not necessarily in its full quality.
   *
   * Also see [isImageDisplayedInFullQuality].
   */
  val isImageDisplayed: Boolean

  /** Whether the image is loaded and displayed in its full quality. */
  val isImageDisplayedInFullQuality: Boolean

  @Suppress("unused")
  @Deprecated("Use isImageDisplayed instead", ReplaceWith("isImageDisplayed"))
  val isImageLoaded: Boolean get() = isImageDisplayed

  @Suppress("unused")
  @Deprecated("Renamed to isImageDisplayedInFullQuality", ReplaceWith("isImageDisplayedInFullQuality"))
  val isImageLoadedInFullQuality: Boolean get() = isImageDisplayedInFullQuality
}
