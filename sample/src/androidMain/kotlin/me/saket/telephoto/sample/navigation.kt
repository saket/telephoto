package me.saket.telephoto.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.rememberCircuitNavigator
import kotlinx.parcelize.Parcelize
import me.saket.telephoto.sample.crop.CropImageScreen
import me.saket.telephoto.sample.crop.CropResultScreen
import me.saket.telephoto.sample.gallery.GalleryScreen
import me.saket.telephoto.sample.gallery.MediaAlbum
import me.saket.telephoto.sample.gallery.MediaItem
import me.saket.telephoto.sample.viewer.MediaViewerScreen
import com.slack.circuit.runtime.screen.Screen as CircuitScreenKey

@Composable
internal fun Navigation(
  initialScreenKey: ScreenKey,
) {
  val backstack = rememberSaveableBackStack(initialScreenKey)
  val navigator = rememberCircuitNavigator(backstack)

  Box(Modifier.fillMaxSize()) {
    val record = backstack.first()
    key(record.key) {
      when (val screenKey = record.screen as ScreenKey) {
        is GalleryScreenKey -> {
          GalleryScreen(
            key = screenKey,
            navigator = navigator,
          )
        }
        is MediaViewerScreenKey -> {
          MediaViewerScreen(
            key = screenKey,
            navigator = navigator,
          )
        }
        is CropImageScreenKey -> {
          CropImageScreen(
            key = screenKey,
            navigator = navigator,
          )
        }
        is CropResultScreenKey -> {
          CropResultScreen(
            key = screenKey,
            navigator = navigator,
          )
        }
      }
    }
  }
}

sealed interface ScreenKey : CircuitScreenKey

@Parcelize
data class GalleryScreenKey(
  val album: MediaAlbum
) : ScreenKey

@Parcelize
data class MediaViewerScreenKey(
  val album: MediaAlbum,
  val initialIndex: Int,
) : ScreenKey

@Parcelize
data class CropImageScreenKey(
  val mediaItem: MediaItem.Image,
) : ScreenKey

@Parcelize
data class CropResultScreenKey(
  val filePath: String,
  val originalSize: String,
  val croppedSize: String,
  val croppedBounds: String,
) : ScreenKey
