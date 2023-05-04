package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoerceInsideTest {
  @Test fun `no zoom`() {
    val layoutSize = Size(1000f, 2000f)
    val contentSize = Size(1000f, 500f)

    assertThat(
      Rect(Offset.Zero, contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset.Zero.copy(x = 100f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset.Zero.copy(y = 100f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset(x = 100f, y = 100f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset.Zero.copy(x = -100f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset.Zero.copy(y = -100f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset(x = -100f, y = -100f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)
  }

  @Test fun `horizontal movement when zoomed content is bigger than layout size in width`() {
    val layoutSize = Size(1000f, 2000f)
    val contentSize = Size(2000f, 1000f)

    assertThat(
      Rect(Offset.Zero, contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset(x = -500f, y = 0f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -500f, y = 0f))

    assertThat(
      Rect(Offset(x = -1000f, y = 0f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -1000f, y = 0f))

    // Shouldn't be able to go left any further.
    assertThat(
      Rect(Offset(x = -1001f, y = 0f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -1000f, y = 0f))

    // Same for the right side.
    assertThat(
      Rect(Offset(x = 500f, y = 0f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset(x = 0f, y = 0f))
  }

  @Test fun `vertical movement when zoomed content is bigger than layout size in height`() {
    val layoutSize = Rect(Offset.Zero, Size(1000f, 2000f))
    val contentSize = Size(1000f, 3000f)

    // todo.
  }

  @Test fun `2d movement when zoomed content is bigger than layout size in both width and height`() {
    val layoutSize = Size(1000f, 2000f)
    val contentSize = Size(2000f, 3000f)

    assertThat(
      Rect(Offset.Zero, contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    // When content is at 0,0 it can't be panned R-to-L any further.
    assertThat(
      Rect(Offset(x = 10f, 20f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset(x = -250f, y = -400f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -250f, y = -400f))

    assertThat(
      Rect(Offset(x = -750f, y = -600f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -750f, y = -600f))

    assertThat(
      Rect(Offset(x = -1005f, y = -1007f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -1000f, y = -1000f))

    assertThat(
      Rect(Offset(x = -1000f, y = 0f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    ).isEqualTo(Offset(-1000f, 0f))
  }

  @Test fun `2d movement when zoomed content is bigger than layout size in width`() {
    assertThat(
      Rect(Offset(x = -100f, y = 0f), Size(800f, 1300f)).topLeftCoercedInside(
        destination = Size(640.0f, 1500.0f),
        alignment = Alignment.TopCenter
      )
    ).isEqualTo(Offset(x = -100f, y = 0f))
  }
}

private fun Rect.topLeftCoercedInside(destination: Size, alignment: Alignment): Offset {
  return calculateTopLeftToOverlapWith(
    destination = destination,
    alignment = alignment,
    layoutDirection = LayoutDirection.Ltr
  )
}
