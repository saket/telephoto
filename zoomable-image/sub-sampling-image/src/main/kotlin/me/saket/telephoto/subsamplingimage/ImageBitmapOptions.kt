package me.saket.telephoto.subsamplingimage

import android.graphics.Bitmap
import android.os.Build.VERSION.SDK_INT
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmapConfig

@Immutable
data class ImageBitmapOptions(
  val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
) {
  companion object {
    val Default = ImageBitmapOptions()
  }
}

fun ImageBitmapOptions(from: Bitmap): ImageBitmapOptions {
  return ImageBitmapOptions(
    config = from.config.toComposeConfig()
  )
}

private fun Bitmap.Config.toComposeConfig(): ImageBitmapConfig {
  return when {
    SDK_INT >= 26 && this == Bitmap.Config.HARDWARE -> ImageBitmapConfig.Gpu
    SDK_INT >= 26 && this == Bitmap.Config.RGBA_F16 -> ImageBitmapConfig.F16
    this == Bitmap.Config.ARGB_8888 -> ImageBitmapConfig.Argb8888
    this == Bitmap.Config.RGB_565 -> ImageBitmapConfig.Rgb565
    this == Bitmap.Config.ALPHA_8 -> ImageBitmapConfig.Alpha8
    else -> ImageBitmapConfig.Argb8888
  }
}

internal fun ImageBitmapConfig.toAndroidConfig(): Bitmap.Config {
  return when {
    SDK_INT >= 26 && this == ImageBitmapConfig.Gpu -> Bitmap.Config.HARDWARE
    SDK_INT >= 26 && this == ImageBitmapConfig.F16 -> Bitmap.Config.RGBA_F16
    this == ImageBitmapConfig.Argb8888 -> Bitmap.Config.ARGB_8888
    this == ImageBitmapConfig.Rgb565 -> Bitmap.Config.RGB_565
    this == ImageBitmapConfig.Alpha8 -> Bitmap.Config.ALPHA_8
    else -> Bitmap.Config.ARGB_8888
  }
}
