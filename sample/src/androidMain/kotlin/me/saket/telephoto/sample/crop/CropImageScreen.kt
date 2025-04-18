package me.saket.telephoto.sample.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.request.ImageRequest
import com.slack.circuit.runtime.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.sample.CropImageScreenKey
import me.saket.telephoto.sample.CropResultScreenKey
import me.saket.telephoto.sample.gallery.MediaItem
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.spatial.SpatialOffset
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomableContent
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import android.util.Size as AndroidSize

@Composable
internal fun CropImageScreen(key: CropImageScreenKey, navigator: Navigator) {
  Scaffold(
    contentWindowInsets = WindowInsets.safeGestures,
  ) { contentPadding ->
    Column(Modifier.padding(contentPadding)) {
      val imageState = rememberZoomableImageState()
      val cropperState = rememberCropperState(imageState)

      Box(Modifier.weight(1f)) {
        // todo: i need contentBounds
        ZoomableAsyncImage(
          modifier = Modifier.fillMaxSize(),
          state = imageState,
          model = ImageRequest.Builder(LocalContext.current)
            .data(key.mediaItem.fullSizedUrl)
            .placeholderMemoryCacheKey(key.mediaItem.placeholderImageUrl)
            .build(),
          contentDescription = key.mediaItem.caption,
        )

        if (!cropperState.cropBounds.isEmpty) {
          CropHandles(
            modifier = Modifier.fillMaxSize(),
            state = cropperState,
          )
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = { navigator.pop() }) {
          Text("Cancel")
        }

        val cropRequests = remember { Channel<Unit>() }
        var isCropping by remember { mutableStateOf(false) }

        Button(
          modifier = Modifier.animateContentSize(),
          onClick = { cropRequests.trySend(Unit) },
          enabled = imageState.isImageDisplayed && !isCropping,
        ) {
          Text(if (isCropping) "Saving…" else "Save")
        }

        val context = LocalContext.current
        LaunchedEffect(cropperState) {
          cropRequests.receiveAsFlow()
            .onEach { isCropping = true }
            .map {
              cropImage(
                context = context,
                cropperState = cropperState,
                mediaItem = key.mediaItem,
              )
            }
            .onEach { isCropping = false }
            .collect(navigator::goTo)
        }
      }
    }
  }
}

@OptIn(ExperimentalTelephotoApi::class, ExperimentalCoilApi::class)
private suspend fun cropImage(
  context: Context,
  cropperState: CropperState,
  mediaItem: MediaItem.Image,
): CropResultScreenKey {
  val cropBounds = cropperState.cropBounds
  val zoomableState = cropperState.imageState.zoomableState

  // todo: improve this code
  val boundsInImage = with(zoomableState.coordinateSystem) {
    val topLeft = SpatialOffset(cropBounds.topLeft, CoordinateSpace.Viewport)
    val bottomRight = SpatialOffset(cropBounds.bottomRight, CoordinateSpace.Viewport)
    Rect(
      topLeft = topLeft.offsetIn(CoordinateSpace.ZoomableContent),
      bottomRight = bottomRight.offsetIn(CoordinateSpace.ZoomableContent)
    ).roundToIntRect()
  }

  val originalImage = withContext(Dispatchers.IO) {
    context.imageLoader.diskCache!!
      .openSnapshot(mediaItem.fullSizedUrl)
      ?: error("image not in cache?")
  }

  lateinit var originalSize: AndroidSize

  val croppedImage = withContext(Dispatchers.IO) {
    ImageDecoder.decodeBitmap(
      ImageDecoder.createSource(originalImage.data.toFile())
    ) { decoder, info, _ ->
      originalSize = info.size
      decoder.crop = boundsInImage.toAndroidRect()
    }
  }

  val cacheDir = withContext(Dispatchers.IO) { context.cacheDir }
  val imagePath = cacheDir.toOkioPath() / "cropped_image_${System.currentTimeMillis()}.jpg"

  val fs = FileSystem.SYSTEM
  withContext(Dispatchers.IO) {
    fs.write(imagePath) {
      croppedImage.compress(
        Bitmap.CompressFormat.JPEG,
        100,
        this.outputStream(),
      )
    }
  }

  return CropResultScreenKey(
    filePath = imagePath.toString(),
    originalSize = "${originalSize.width} x ${originalSize.height} px",
    croppedSize = "Size: ${croppedImage.width} x ${croppedImage.height} px",
    croppedBounds = "Bounds: ${boundsInImage.topLeft} – ${boundsInImage.bottomRight}",
  )
}

@Stable
private fun PaddingValues.union(other: PaddingValues): PaddingValues {
  return UnionPaddingValues(this, other)
}

private data class UnionPaddingValues(
  private val first: PaddingValues,
  private val second: PaddingValues,
) : PaddingValues {
  override fun calculateBottomPadding(): Dp {
    return maxOf(first.calculateBottomPadding(), second.calculateBottomPadding())
  }

  override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
    return maxOf(first.calculateLeftPadding(layoutDirection), second.calculateRightPadding(layoutDirection))
  }

  override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
    return maxOf(first.calculateRightPadding(layoutDirection), second.calculateRightPadding(layoutDirection))
  }

  override fun calculateTopPadding(): Dp {
    return maxOf(first.calculateTopPadding(), second.calculateTopPadding())
  }
}
