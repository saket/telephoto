package me.saket.telephoto.subsamplingimage

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Stable

sealed interface ImageSource {
  companion object {
    @Stable
    fun asset(name: String): ImageSource {
      return AssetImageSource(name)
    }

    @Stable
    fun resource(@DrawableRes id: Int): ImageSource {
      return ResourceImageSource(id)
    }
  }
}

internal data class AssetImageSource(val assetName: String) : ImageSource

internal data class ResourceImageSource(@DrawableRes val id: Int) : ImageSource
