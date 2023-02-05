@file:Suppress("DataClassPrivateConstructor")

package me.saket.telephoto.subsamplingimage

sealed interface ImageSource {
  companion object {
    fun asset(name: String): ImageSource = AssetImageSource(name)
  }
}

internal data class AssetImageSource(
  val assetName: String
) : ImageSource
