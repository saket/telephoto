package me.saket.telephoto.sample

import android.os.Bundle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import kotlinx.parcelize.Parcelize
import me.saket.telephoto.sample.gallery.GalleryScreen
import me.saket.telephoto.sample.gallery.MediaAlbum
import me.saket.telephoto.sample.viewer.MediaViewerScreen

@Composable
fun AppCompatActivity.Navigation(
  initialScreenKey: GalleryScreenKey,
) {
  val navigator = rememberSaveable(saver = OkayishNavigator.Saver) {
    OkayishNavigator(listOf(initialScreenKey))
  }
  LaunchedEffect(navigator) {
    onBackPressedDispatcher.addCallback {
      navigator.pop()
    }
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
  initialScreens: List<ScreenKey>,
) : Navigator {
  val backstack = mutableStateListOf(*initialScreens.toTypedArray())

  override fun lfg(screen: ScreenKey) {
    backstack.add(screen)
  }

  fun pop() {
    backstack.removeLast()
  }

  @Composable
  fun backPressedDispatcherOwner(): OnBackPressedDispatcherOwner {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return remember(this, lifecycle) {
      object : OnBackPressedDispatcherOwner {
        override val lifecycle: Lifecycle get() = lifecycle
        override val onBackPressedDispatcher = OnBackPressedDispatcher { pop() }
      }
    }
  }

  companion object {
    val Saver = Saver<OkayishNavigator, Bundle>(
      save = { navigator ->
        bundleOf("backstack" to ArrayList(navigator.backstack))
      },
      restore = {
        OkayishNavigator(
          it.getParcelableArrayList<GalleryScreenKey>("backstack") as List<GalleryScreenKey>
        )
      }
    )
  }
}
