package me.saket.telephoto.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.ui.Ui
import kotlinx.parcelize.Parcelize
import me.saket.telephoto.sample.crop.CropImageScreen
import me.saket.telephoto.sample.crop.CropResultScreen
import me.saket.telephoto.sample.gallery.GalleryScreen
import me.saket.telephoto.sample.gallery.MediaAlbum
import me.saket.telephoto.sample.gallery.MediaItem
import me.saket.telephoto.sample.viewer.MediaViewerScreen
import com.slack.circuit.runtime.screen.StaticScreen as CircuitScreenKey

@Composable
internal fun Navigation(
  initialScreenKey: ScreenKey,
) {
  val backstack = rememberSaveableBackStack(initialScreenKey)
  val navigator = rememberCircuitNavigator(backstack)

  val circuit = remember {
    val uiFactory = Ui.Factory { screen, _ ->
      object : Ui<CircuitUiState> {
        @Composable
        override fun Content(state: CircuitUiState, modifier: Modifier) {
          when (screen) {
            is GalleryScreenKey -> {
              GalleryScreen(
                key = screen,
                navigator = navigator,
              )
            }
            is MediaViewerScreenKey -> {
              MediaViewerScreen(
                key = screen,
                navigator = navigator,
              )
            }
            is CropImageScreenKey -> {
              CropImageScreen(
                key = screen,
                navigator = navigator,
              )
            }
            is CropResultScreenKey -> {
              CropResultScreen(
                key = screen,
                navigator = navigator,
              )
            }
          }
        }
      }
    }
    Circuit.Builder()
      .addUiFactory(uiFactory)
      .build()
  }

  NavigableCircuitContent(
    circuit = circuit,
    navigator = navigator,
    backStack = backstack,
  )
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
