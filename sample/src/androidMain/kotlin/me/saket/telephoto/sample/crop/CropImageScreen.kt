package me.saket.telephoto.sample.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
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
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomableContent
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.spatial.SpatialRect
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import android.util.Size as AndroidSize

@Composable
internal fun CropImageScreen(key: CropImageScreenKey, navigator: Navigator) {
  Scaffold(
    contentWindowInsets = WindowInsets.safeGestures,
  ) { contentPadding ->
    Column {
      val imageState = rememberZoomableImageState()
      val cropperState = rememberCropperState(imageState)

      Box(
        Modifier
          .padding(contentPadding)
          .weight(1f)
          .padding(horizontal = 16.dp)
      ) {
        // todo: i need contentPadding
        ZoomableAsyncImage(
          modifier = Modifier
            .fillMaxSize()
            .disallowTouchEventsOutsideOf { cropperState.cropBounds },
          state = imageState,
          model = ImageRequest.Builder(LocalContext.current)
            .data(key.mediaItem.fullSizedUrl)
            .placeholderMemoryCacheKey(key.mediaItem.placeholderImageUrl)
            .build(),
          contentDescription = key.mediaItem.caption,
        )

        if (!cropperState.cropBounds.isEmpty) {
          CropHandles(
            modifier = Modifier.matchParentSize(),
            state = cropperState,
          )
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp))
          .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Bottom))
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
  val originalImage = withContext(Dispatchers.IO) {
    context.imageLoader.diskCache!!
      .openSnapshot(mediaItem.fullSizedUrl)
      ?: error("image not in cache?")
  }

  val zoomableState = cropperState.imageState.zoomableState
  val cropBoundsInImage = with(zoomableState.coordinateSystem) {
    val spatial = SpatialRect(cropperState.cropBounds, CoordinateSpace.Viewport)
    spatial.rectIn(CoordinateSpace.ZoomableContent)
  }

  lateinit var originalSize: AndroidSize
  val croppedImage = withContext(Dispatchers.IO) {
    ImageDecoder.decodeBitmap(
      ImageDecoder.createSource(originalImage.data.toFile())
    ) { decoder, info, _ ->
      originalSize = info.size
      decoder.crop = cropBoundsInImage.roundToIntRect().toAndroidRect()
    }
  }

  val fs = FileSystem.SYSTEM
  val imagePath = withContext(Dispatchers.IO) { // Because Context#cacheDir performs IO.
    (context.cacheDir.toOkioPath() / "cropped_image_${System.currentTimeMillis()}.jpg")
  }
  withContext(Dispatchers.IO) {
    fs.write(imagePath) {
      croppedImage.compress(
        /* format = */ Bitmap.CompressFormat.JPEG,
        /* quality = */ 100,
        /* stream = */ this.outputStream(),
      )
    }
  }

  return CropResultScreenKey(
    filePath = imagePath.toString(),
    originalSize = "${originalSize.width} x ${originalSize.height} px",
    croppedSize = "Size: ${croppedImage.width} x ${croppedImage.height} px",
    croppedBounds = "Bounds: ${cropBoundsInImage.topLeft} – ${cropBoundsInImage.bottomRight}",
  )
}

internal fun Modifier.disallowTouchEventsOutsideOf(bounds: () -> Rect): Modifier {
  return pointerInput(bounds) {
    awaitEachGesture {
      val downEvent = awaitFirstDown(requireUnconsumed = false)
      if (!bounds().contains(downEvent.position)) {
        // Gesture started outside the allowed bounds, consume all events.
        do {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          event.changes.fastForEach { it.consume() }
        } while (event.changes.fastAny { it.pressed })
      }
    }
  }
}
