package me.saket.telephoto.subsamplingimage

import android.net.Uri
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

    @Stable
    fun contentUri(uri: Uri): ImageSource {
      return UriImageSource(uri)
    }
  }
}

internal data class AssetImageSource(val assetName: String) : ImageSource

internal data class ResourceImageSource(@DrawableRes val id: Int) : ImageSource

internal data class UriImageSource(val uri: Uri): ImageSource
