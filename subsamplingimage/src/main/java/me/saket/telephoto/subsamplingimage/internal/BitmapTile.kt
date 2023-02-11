package me.saket.telephoto.subsamplingimage.internal

import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap

internal typealias BitmapTileGrid = Map<BitmapSampleSize, List<BitmapTile>>

@Immutable
internal data class BitmapTile( // todo: rename to BitmapRegionTIle
  val bitmap: ImageBitmap? = null,
  val sampleSize: BitmapSampleSize,

  val regionBounds: BitmapRegionBounds,

  /** [regionBounds] x (scale + pan + rotation). */
  val drawBounds: Rect = Rect.Zero,

  // TODO: consider remove this in favor of filtering out invisible tiles.
  val isVisible: Boolean = true
)

@JvmInline
internal value class BitmapRegionBounds(
  val bounds: Rect,
)

/** See [BitmapFactory.Options.inSampleSize]. */
@JvmInline
internal value class BitmapSampleSize(val size: Int) {
  companion object; // For extensions.

  init {
    check(size == 1 || size.rem(2) == 0) {
      "Incorrect size = $size. BitmapRegionDecoder requires values based on powers of 2."
    }
  }
}
