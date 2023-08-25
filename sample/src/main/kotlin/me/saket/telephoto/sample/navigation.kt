package me.saket.telephoto.sample

import android.os.Parcelable
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.addCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.parcelize.Parcelize
import me.saket.telephoto.sample.gallery.GalleryScreen
import me.saket.telephoto.sample.gallery.MediaAlbum
import me.saket.telephoto.sample.viewer.MediaViewerScreen

@Composable
fun AppCompatActivity.Navigation(
  initialScreenKey: GalleryScreenKey,
) {
  val navigator = remember {
    OkayishNavigator(
      initialScreenKey = initialScreenKey,
      parentBackPressedDispatcher = onBackPressedDispatcher,
    )
  }

  Box(Modifier.fillMaxSize()) {
    CompositionLocalProvider(
      LocalOnBackPressedDispatcherOwner provides navigator.backPressedDispatcherOwner()
    ) {
      for (screen in navigator.backstack) {
        when (screen) {
          is GalleryScreenKey -> {
            GalleryScreen(
              key = initialScreenKey,
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

fun interface Navigator {
  fun lfg(screen: ScreenKey)
}

sealed interface ScreenKey : Parcelable

@Parcelize
data class GalleryScreenKey(
  val album: MediaAlbum
) : ScreenKey

@Parcelize
data class MediaViewerScreenKey(
  val album: MediaAlbum,
  val initialIndex: Int,
) : ScreenKey

@Stable
private class OkayishNavigator(
  initialScreenKey: GalleryScreenKey,
  parentBackPressedDispatcher: OnBackPressedDispatcher,
) : Navigator {
  val backstack = mutableStateListOf<ScreenKey>(initialScreenKey)

  init {
    parentBackPressedDispatcher.addCallback {
      backstack.removeLast()
    }
  }

  override fun lfg(screen: ScreenKey) {
    backstack.add(screen)
  }

  @Composable
  fun backPressedDispatcherOwner(): OnBackPressedDispatcherOwner {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return remember(this, lifecycle) {
      object : OnBackPressedDispatcherOwner {
        override val lifecycle: Lifecycle get() = lifecycle
        override val onBackPressedDispatcher = OnBackPressedDispatcher { backstack.removeLast() }
      }
    }
  }
}
