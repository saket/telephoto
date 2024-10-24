@file:Suppress("NOTHING_TO_INLINE")

package me.saket.telephoto.subsamplingimage.internal

import android.graphics.Matrix
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata.ImageOrientation
import kotlin.LazyThreadSafetyMode.NONE

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

private val sourceCoordinates = FloatArray(8)
private val destinationCoordinates = FloatArray(8)
private val matrix by lazy(NONE) { Matrix() }

/**
 * Creates a [Matrix] that can be used for drawing this tile's rotated bitmap such that
 * it appears straight on the canvas.
 *
 * Voodoo code copied from https://github.com/davemorrissey/subsampling-scale-image-view.
 * I don't fully understand how the matrix is created, but it works.
 */
internal inline fun CanvasRegionTile.createRotationMatrix(): Matrix {
  val bitmap = checkNotNull(bitmap)
  matrix.reset()
  sourceCoordinates.set(
    0,
    0,
    bitmap.width,
    0,
    bitmap.width,
    bitmap.height,
    0,
    bitmap.height,
  )
  when (orientation) {
    ImageOrientation.None -> {
      destinationCoordinates.set(
        bounds.left,
        bounds.top,
        bounds.right,
        bounds.top,
        bounds.right,
        bounds.bottom,
        bounds.left,
        bounds.bottom,
      )
    }
    ImageOrientation.Orientation90 -> {
      destinationCoordinates.set(
        bounds.right,
        bounds.top,
        bounds.right,
        bounds.bottom,
        bounds.left,
        bounds.bottom,
        bounds.left,
        bounds.top,
      )
    }
    ImageOrientation.Orientation180 -> {
      destinationCoordinates.set(
        bounds.right,
        bounds.bottom,
        bounds.left,
        bounds.bottom,
        bounds.left,
        bounds.top,
        bounds.right,
        bounds.top,
      )
    }
    ImageOrientation.Orientation270 -> {
      destinationCoordinates.set(
        bounds.left,
        bounds.bottom,
        bounds.left,
        bounds.top,
        bounds.right,
        bounds.top,
        bounds.right,
        bounds.bottom,
      )
    }
  }
  matrix.setPolyToPoly(
    /* src = */ sourceCoordinates,
    /* srcIndex = */ 0,
    /* dst = */ destinationCoordinates,
    /* dstIndex = */ 0,
    /* pointCount = */ 4
  )
  return matrix
}

private inline fun FloatArray.set(
  f0: Int,
  f1: Int,
  f2: Int,
  f3: Int,
  f4: Int,
  f5: Int,
  f6: Int,
  f7: Int,
) {
  this[0] = f0.toFloat()
  this[1] = f1.toFloat()
  this[2] = f2.toFloat()
  this[3] = f3.toFloat()
  this[4] = f4.toFloat()
  this[5] = f5.toFloat()
  this[6] = f6.toFloat()
  this[7] = f7.toFloat()
}
