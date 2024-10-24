package me.saket.telephoto.subsamplingimage.internal

import android.os.Parcelable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntRect
import kotlinx.parcelize.Parcelize
import me.saket.telephoto.subsamplingimage.RealSubSamplingImageState

@OptIn(ExperimentalFoundationApi::class)
internal data class SubSamplingLayoutItemProvider(
  private val state: RealSubSamplingImageState,
  private val itemContent: @Composable (ImageRegionTile) -> Unit,
) : LazyLayoutItemProvider {

  override val itemCount: Int
    get() = state.viewportTiles.size

  override fun getKey(index: Int): ItemKey {
    val tile = state.viewportTiles[index]
    val isBase = tile.region == state.tileGrid!!.base

    return if (isBase) {
      ItemKey.BaseTileKey(
        ParcelableRegion(tile.region)
      )
    } else {
      ItemKey.ForegroundTileKey(ParcelableRegion(tile.region))
    }
  }

  @Composable
  override fun Item(index: Int, key: Any) {
    check(key is ItemKey)
    itemContent(key.region.region)
  }

  sealed interface ItemKey : Parcelable {
    val region: ParcelableRegion

    @Parcelize
    data class BaseTileKey(override val region: ParcelableRegion) : ItemKey

    @Parcelize
    data class ForegroundTileKey(override val region: ParcelableRegion) : ItemKey
  }

  // todo: this is terrible. should BitmapRegionTile be parcelable?
  //   or i could write a parceler for BitmapRegionTile.
  @Parcelize
  data class ParcelableRegion(
    val sampleSize: Int,
    val boundsLeft: Int,
    val boundsTop: Int,
    val boundsRight: Int,
    val boundsBottom: Int,
  ) : Parcelable {
    constructor(region: ImageRegionTile) : this(
      sampleSize = region.sampleSize.size,
      boundsLeft = region.bounds.left,
      boundsTop = region.bounds.top,
      boundsRight = region.bounds.right,
      boundsBottom = region.bounds.bottom,
    )

    val region
      get() = ImageRegionTile(
        sampleSize = BitmapSampleSize(sampleSize),
        bounds = IntRect(
          left = boundsLeft,
          top = boundsTop,
          right = boundsRight,
          bottom = boundsBottom,
        )
      )
  }
}
