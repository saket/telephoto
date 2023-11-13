package me.saket.telephoto.subsamplingimage

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import me.saket.telephoto.subsamplingimage.internal.CanvasRegionTile

/** State for [SubSamplingImage]. */
@Stable
internal class RealSubSamplingImageState(
  private val imageSource: SubSamplingImageSource
) : SubSamplingImageState {
  override var imageSize: IntSize? by mutableStateOf(null)

  /**
   * Whether all the visible tiles have been loaded and the image is displayed (not necessarily in its full quality).
   *
   * Also see [isImageLoadedInFullQuality].
   */
  override val isImageLoaded: Boolean by derivedStateOf {
    canvasSize != null && tiles.isNotEmpty()
      && (tiles.fastAny { it.isBaseTile && it.bitmap != null } || tiles.fastAll { it.bitmap != null })
  }

  /** Whether all the visible and *full resolution* tiles have been loaded and the image is displayed. */
  override val isImageLoadedInFullQuality: Boolean by derivedStateOf {
    isImageLoaded && tiles.fastAll { it.bitmap != null && it.bitmap != imageSource.preview }
  }

  internal var tiles by mutableStateOf(emptyList<CanvasRegionTile>())
  internal var canvasSize: IntSize? by mutableStateOf(null)
  internal var showTileBounds = false  // Only used by tests.
}
