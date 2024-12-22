package me.saket.telephoto.subsamplingimage.internal

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter

@Immutable
internal data class RotatedBitmapPainter(
  private val image: Bitmap,
  private val orientation: ExifMetadata.ImageOrientation,
) : Painter() {
  override val intrinsicSize: Size
    get() = Size(image.width.toFloat(), image.height.toFloat())

  // Note to self: Compose UI's Paint() enables anti-aliasing
  // and bilinear sampling on scaled bitmaps by default.
  private val paint = Paint()

  override fun applyAlpha(alpha: Float): Boolean {
    this.paint.alpha = alpha
    return true
  }

  override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
    this.paint.colorFilter = colorFilter
    return true
  }

  override fun DrawScope.onDraw() {
    val rotationMatrix = createRotationMatrix(
      bitmapSize = intrinsicSize,
      orientation = orientation,
      bounds = size,
    )
    drawIntoCanvas {
      it.nativeCanvas.drawBitmap(
        /* bitmap = */ image,
        /* matrix = */ rotationMatrix,
        /* paint = */ paint.asFrameworkPaint(),
      )
    }
  }
}
