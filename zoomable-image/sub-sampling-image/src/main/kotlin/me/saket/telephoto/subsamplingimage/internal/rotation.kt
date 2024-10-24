@file:Suppress("NOTHING_TO_INLINE")

package me.saket.telephoto.subsamplingimage.internal

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.toRect
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata.ImageOrientation
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.math.roundToInt

/**
 * Calculate the position of this rectangle inside [unRotatedParent]
 * after its parent is rotated clockwise by [degrees].
 */
internal fun IntRect.rotateBy(degrees: Int, unRotatedParent: IntRect): IntRect {
  if (degrees == 0) {
    return this
  }

  // There is probably a better (and simpler) way to find the rectangle after
  // rotation, but I'm brute forcing my way through this by manually mapping points.
  val newTopLeft = when (degrees) {
    -270, 90 -> {
      val offsetFromBottomLeft = unRotatedParent.bottomLeft - bottomLeft
      IntOffset(
        x = offsetFromBottomLeft.flip().x,
        y = -offsetFromBottomLeft.flip().y,
      )
    }

    -180, 180 -> {
      unRotatedParent.bottomRight - bottomRight
    }

    -90, 270 -> {
      val offsetFromTopRight = unRotatedParent.topRight - topRight
      IntOffset(
        x = -offsetFromTopRight.flip().x,
        y = offsetFromTopRight.flip().y,
      )
    }

    0, 360 -> topLeft
    else -> error("unsupported orientation = $degrees")
  }

  return IntRect(
    offset = newTopLeft,
    size = when (degrees) {
      -270, 90 -> size.flip()
      -180, 180 -> size
      -90, 270 -> size.flip()
      0, 360 -> size
      else -> error("unsupported orientation = $degrees")
    },
  )
}

private fun IntOffset.flip(): IntOffset = IntOffset(x = y, y = x)

private fun IntSize.flip(): IntSize = IntSize(width = height, height = width)

// The same instance is shared by all calls to createRotationMatrix()
// because it's always called on the same (main) thread.
private val matrix by lazy(NONE) { Matrix() }

/**
 * Creates a [Matrix] that can be used for drawing this tile's rotated bitmap such that
 * it appears straight on the canvas.
 *
 * Code adapted from [subsampling-scale-image-view](https://github.com/davemorrissey/subsampling-scale-image-view).
 */
internal inline fun createRotationMatrix(
  bitmapSize: Size,
  orientation: ImageOrientation,
  bounds: Size,
): Matrix {
  matrix.reset()
  if (orientation == ImageOrientation.None) {
    return matrix
  }

  // Translate to bitmap center.
  val bitmapCenterX = (bitmapSize.width / 2f).roundToInt().toFloat()
  val bitmapCenterY = (bitmapSize.height / 2f).roundToInt().toFloat()
  matrix.postTranslate(-bitmapCenterX, -bitmapCenterY)

  @Suppress("KotlinConstantConditions")
  val rotationDegrees = when (orientation) {
    ImageOrientation.None -> error("unreachable code")
    ImageOrientation.Orientation90 -> 90f
    ImageOrientation.Orientation180 -> 180f
    ImageOrientation.Orientation270 -> 270f
  }
  matrix.postRotate(rotationDegrees)

  val scale = if (rotationDegrees % 180 == 0f) {
    minOf(
      bounds.width / bitmapSize.width,
      bounds.height / bitmapSize.height,
    )
  } else {
    minOf(
      bounds.width / bitmapSize.height,
      bounds.height / bitmapSize.width,
    )
  }
  matrix.postScale(scale, scale)

  // Translate to final position, ensuring pixel alignment.
  val centerX = (bounds.width / 2f).roundToInt().toFloat()
  val centerY = (bounds.height / 2f).roundToInt().toFloat()
  matrix.postTranslate(centerX, centerY)
  return matrix
}
