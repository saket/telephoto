package me.saket.telephoto.zoomable.spatial

import androidx.compose.runtime.Immutable
import androidx.compose.ui.layout.ScaleFactor
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.internal.maxScale

@Poko
@Immutable
@ExperimentalTelephotoApi
class SpatialScaleFactor(
  // todo: probably doesn't make sense to represent scales on both axes. user zoom only happens on both axes, no?
  scaleFactor: ScaleFactor,
  val space: CoordinateSpace,
) {
  val scaleFactor: Float = scaleFactor.maxScale

  constructor(scaleFactor: Float, space: CoordinateSpace) : this(ScaleFactor(scaleFactor, scaleFactor), space)

  override fun toString(): String {
    return "SpatialScaleFactor(${this.scaleFactor}, $space)"
  }

  companion object;
}
