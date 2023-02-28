package me.saket.telephoto.sample

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.composable.Children
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.node.ParentNode
import com.bumble.appyx.core.node.node
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.push
import com.bumble.appyx.navmodel.backstack.transitionhandler.rememberBackstackFader
import com.bumble.appyx.navmodel.backstack.transitionhandler.rememberBackstackSlider
import kotlinx.parcelize.Parcelize
import me.saket.telephoto.sample.gallery.GalleryScreen
import me.saket.telephoto.sample.gallery.MediaAlbum
import me.saket.telephoto.sample.viewer.MediaViewerScreen

class RootNode(
  buildContext: BuildContext,
  initialScreen: ScreenKey,
  private val backStack: BackStack<ScreenKey> = BackStack(
    initialElement = initialScreen,
    savedStateMap = buildContext.savedStateMap,
  )
) : ParentNode<ScreenKey>(
  navModel = backStack,
  buildContext = buildContext
) {

  @Composable
  override fun View(modifier: Modifier) {
    Children(
      navModel = backStack,
      transitionHandler = rememberBackstackFader()
    )
  }

  override fun resolve(navTarget: ScreenKey, buildContext: BuildContext): Node {
    val navigator = Navigator { target: ScreenKey ->
      backStack.push(target)
    }

    return node(buildContext) {
      when (navTarget) {
        is GalleryScreenKey -> GalleryScreen(navTarget, navigator)
        is MediaViewerScreenKey -> MediaViewerScreen(navTarget)
      }
    }
  }
}

@Immutable
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
