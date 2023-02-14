package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun Size.roundToIntSize(): IntSize {
  return IntSize(width.roundToInt(), height.roundToInt())
}

internal operator fun Size.times(scale: ScaleFactor): Size {
  return Size(
    width = width * scale.scaleX,
    height = height * scale.scaleY,
  )
}

/**
 * Copied from [androidx samples](https://github.com/androidx/androidx/blob/643b1cfdd7dfbc5ccce1ad951b6999df049678b3/compose/foundation/foundation/samples/src/main/java/androidx/compose/foundation/samples/TransformGestureSamples.kt#L61).
 *
 * Rotates the given offset around the origin by the given angle in degrees.
 * A positive angle indicates a counterclockwise rotation around the right-handed
 * 2D Cartesian coordinate system.
 *
 * See: [Rotation matrix](https://en.wikipedia.org/wiki/Rotation_matrix)
 */
internal fun Offset.rotateBy(angle: Float): Offset {
  val angleInRadians = angle * PI / 180
  return Offset(
    (x * cos(angleInRadians) - y * sin(angleInRadians)).toFloat(),
    (x * sin(angleInRadians) + y * cos(angleInRadians)).toFloat()
  )
}
