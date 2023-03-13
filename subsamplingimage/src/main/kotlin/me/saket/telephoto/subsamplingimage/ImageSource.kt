package me.saket.telephoto.subsamplingimage

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import okio.buffer
import java.io.InputStream

// todo: doc pointing to companion functions.
sealed interface ImageSource {
  // todo: doc.
  fun stream(context: Context): InputStream

  companion object {
    // todo: suggest remembering this function.
    // todo: address this:
    // I would still prefer not to accept an input stream because it could be partially read, blocking or already closed
    fun stream(factory: (Context) -> okio.Source): ImageSource {
      return StreamingImageSource(factory)
    }

    @Stable
    fun asset(name: String): ImageSource = AssetImageSource(name)

    @Stable
    fun resource(@DrawableRes id: Int): ImageSource = ResourceImageSource(id)

    @Stable
    fun contentUri(uri: Uri): ImageSource = UriImageSource(uri)
  }
}

@Immutable
internal data class StreamingImageSource(val factory: (Context) -> okio.Source) : ImageSource {
  override fun stream(context: Context) =
    factory(context).buffer().inputStream()
}

@Immutable
internal data class AssetImageSource(val assetName: String) : ImageSource {
  override fun stream(context: Context) =
    context.assets.open(assetName, AssetManager.ACCESS_RANDOM)
}

@Immutable
internal data class ResourceImageSource(@DrawableRes val id: Int) : ImageSource {
  @SuppressLint("ResourceType")
  override fun stream(context: Context) =
    context.resources.openRawResource(id)
}

@Immutable
internal data class UriImageSource(val uri: Uri) : ImageSource {
  @SuppressLint("Recycle")
  override fun stream(context: Context): InputStream =
    context.contentResolver.openInputStream(uri) ?: error("Failed to read from uri: $uri")
}
