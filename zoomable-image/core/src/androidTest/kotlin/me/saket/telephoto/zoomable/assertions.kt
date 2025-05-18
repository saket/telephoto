package me.saket.telephoto.zoomable

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ScaleFactor
import assertk.Assert
import assertk.assertions.isCloseTo

fun Assert<ScaleFactor>.isCloseTo(value: ScaleFactor, delta: Float) = given { actual ->
  assertThat(value.scaleX).isCloseTo(actual.scaleX, delta)
  assertThat(value.scaleY).isCloseTo(actual.scaleY, delta)
}

fun Assert<Offset>.isCloseTo(value: Offset, delta: Float) = given { actual ->
  assertThat(value.x).isCloseTo(actual.x, delta)
  assertThat(value.y).isCloseTo(actual.y, delta)
}
