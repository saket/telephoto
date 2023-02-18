package me.saket.telephoto.subsamplingimage

import androidx.compose.runtime.Stable

sealed interface ImageSource {
  companion object {
    @Stable
    fun asset(name: String): ImageSource {
      return AssetImageSource(name)
    }
  }
}

internal data class AssetImageSource(
  val assetName: String,
) : ImageSource
