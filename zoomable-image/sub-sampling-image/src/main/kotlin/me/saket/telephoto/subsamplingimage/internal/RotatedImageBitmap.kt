package me.saket.telephoto.subsamplingimage.internal

import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata
import me.saket.telephoto.subsamplingimage.internal.createRotationMatrix

// todo: should this be public so that anyone can create rotated bitmaps?
@Immutable
internal class RotatedImageBitmap(
  val delegate: ImageBitmap,
  val orientation: ExifMetadata.ImageOrientation,
) : ImageBitmap by delegate

internal class RotatedBitmapPainter(image: ImageBitmap) : Painter() {
  private val image: ImageBitmap = when (image) {
    is RotatedImageBitmap -> image.delegate
    else -> image
  }

  private val orientation: ExifMetadata.ImageOrientation = when (image) {
    is RotatedImageBitmap -> image.orientation
    else -> ExifMetadata.ImageOrientation.None
  }

  override val intrinsicSize: Size
    get() = Size(image.width.toFloat(), image.height.toFloat())

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val rotationMatrix = Cached<Size, Matrix>()

  override fun DrawScope.onDraw() {
    val rotationMatrix = rotationMatrix.getOrCreate(size) {
      createRotationMatrix(
        bitmapSize = intrinsicSize,
        orientation = orientation,
        bounds = size,
      )
    }
    drawIntoCanvas {
      it.nativeCanvas.drawBitmap(
        /* bitmap = */ image.asAndroidBitmap(),
        /* matrix = */ rotationMatrix,
        /* paint = */ paint,
      )
    }
  }
}

private class Cached<K, V> {
  private var cache: Pair<K, V>? = null

  fun getOrCreate(key: K, create: (K) -> V): V {
    val cached = cache?.let { (cachedKey, cachedValue) ->
      cachedValue.takeIf { cachedKey == key }
    }
    return cached ?: create(key).also { value ->
      this.cache = key to value
    }
  }
}
