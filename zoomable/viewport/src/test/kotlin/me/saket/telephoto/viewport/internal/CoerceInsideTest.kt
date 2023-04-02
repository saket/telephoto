package me.saket.telephoto.viewport.internal

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoerceInsideTest {

  @Test fun `no zoom`() {
    val viewport = Rect(Offset.Zero, Size(1000f, 2000f))
    val contentSize = Size(1000f, 500f)

    assertThat(
      Rect(Offset.Zero, contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset.Zero.copy(x = 100f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset.Zero.copy(y = 100f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset(x = 100f, y = 100f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset.Zero.copy(x = -100f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset.Zero.copy(y = -100f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset(x = -100f, y = -100f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)
  }

  @Test fun `horizontal movement when zoomed content is bigger than viewport in width`() {
    val viewport = Rect(Offset.Zero, Size(1000f, 2000f))
    val contentSize = Size(2000f, 1000f)

    assertThat(
      Rect(Offset.Zero, contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset(x = -500f, y = 0f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -500f, y = 0f))

    assertThat(
      Rect(Offset(x = -1000f, y = 0f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -1000f, y = 0f))

    // Shouldn't be able to go left any further.
    assertThat(
      Rect(Offset(x = -1001f, y = 0f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -1000f, y = 0f))

    // Same for the right side.
    assertThat(
      Rect(Offset(x = 500f, y = 0f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset(x = 0f, y = 0f))
  }

  @Test fun `vertical movement when zoomed content is bigger than viewport in height`() {
    val viewport = Rect(Offset.Zero, Size(1000f, 2000f))
    val contentSize = Size(1000f, 3000f)

    // todo.
  }

  @Test fun `2d movement when zoomed content is bigger than viewport in both width and height`() {
    val viewport = Rect(Offset.Zero, Size(1000f, 2000f))
    val contentSize = Size(2000f, 3000f)

    assertThat(
      Rect(Offset.Zero, contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    // When content is at 0,0 it can't be panned R-to-L any further.
    assertThat(
      Rect(Offset(x = 10f, 20f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset(x = -250f, y = -400f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -250f, y = -400f))

    assertThat(
      Rect(Offset(x = -750f, y = -600f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -750f, y = -600f))

    assertThat(
      Rect(Offset(x = -1005f, y = -1007f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -1000f, y = -1000f))

    assertThat(
      Rect(Offset(x = -1000f, y = 0f), contentSize).topLeftCoercedInside(viewport, Alignment.TopCenter)
    ).isEqualTo(Offset(-1000f, 0f))
  }

  @Test fun `2d movement when zoomed content is bigger than viewport in width`() {
    assertThat(
      Rect(Offset(x = -100f, y = 0f), Size(800f, 1300f)).topLeftCoercedInside(
        viewport = Rect(Offset.Zero, Size(640.0f, 1500.0f)),
        alignment = Alignment.TopCenter
      )
    ).isEqualTo(Offset(x = -100f, y = 0f))
  }
}

private fun Rect.topLeftCoercedInside(viewport: Rect, alignment: Alignment): Offset {
  return topLeftCoercedInside(viewport, alignment, LayoutDirection.Ltr)
}
