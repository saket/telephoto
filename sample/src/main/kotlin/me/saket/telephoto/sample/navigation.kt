package me.saket.telephoto.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.push
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.foundation.screen
import kotlinx.parcelize.Parcelize
import me.saket.telephoto.sample.gallery.GalleryScreen
import me.saket.telephoto.sample.gallery.MediaAlbum
import me.saket.telephoto.sample.viewer.MediaViewerScreen
import com.slack.circuit.runtime.Screen as CircuitScreenKey

@Composable
fun Navigation(
  initialScreenKey: ScreenKey,
) {
  val backstack = rememberSaveableBackStack { push(initialScreenKey) }
  val navigator = rememberCircuitNavigator(backstack)

  Box(Modifier.fillMaxSize()) {
    for (record in backstack.take(2).asReversed()) {
      key(record.key) {
        when (val screen = record.screen) {
          is GalleryScreenKey -> {
            GalleryScreen(
              key = screen,
              navigator = navigator
            )
          }
          is MediaViewerScreenKey -> {
            MediaViewerScreen(
              key = screen
            )
          }
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
