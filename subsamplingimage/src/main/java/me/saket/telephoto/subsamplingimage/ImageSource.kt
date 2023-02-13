package me.saket.telephoto.subsamplingimage

import androidx.compose.runtime.Stable

sealed interface ImageSource {
  val contentDescription: String?

  companion object {
    @Stable
    fun asset(name: String, contentDescription: String?): ImageSource {
      return AssetImageSource(
        assetName = name,
        contentDescription = contentDescription
      )
    }
  }
}

internal data class AssetImageSource(
  val assetName: String,
  override val contentDescription: String?
) : ImageSource
