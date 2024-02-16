package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class DimensTest {
  @Test fun `rects scaled by 0 should have 0 size`() {
    val rect = IntRect(
      IntOffset(40, 40),
      IntSize(100, 100)
    ).scaledAndOffsetBy(ScaleFactor(0f, 0f), Offset(200f, 200f))
    assertThat(rect.size).isEqualTo(Size.Zero)
  }
}
