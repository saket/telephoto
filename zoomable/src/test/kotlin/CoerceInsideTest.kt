package me.saket.telephoto.zoomable

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertThat
import me.saket.telephoto.zoomable.internal.coerceInside
import org.junit.Test

class CoerceInsideTest {

  @Test fun `no zoom`() {
    val viewport = Rect(Offset.Zero, Size(1000f, 2000f))
    val content = Rect(Offset.Zero, Size(1000f, 500f))

    assertThat(
      content.coerceInside(viewport, targetOffset = Offset.Zero)
    ).isEqualTo(Offset.Zero)

    assertThat(
      content.coerceInside(viewport, targetOffset = Offset.Zero.copy(x = 100f))
    ).isEqualTo(Offset.Zero)

    assertThat(
      content.coerceInside(viewport, targetOffset = Offset.Zero.copy(y = 100f))
    ).isEqualTo(Offset.Zero)

    assertThat(
      content.coerceInside(viewport, targetOffset = Offset(x = 100f, y = 100f))
    ).isEqualTo(Offset.Zero)

    assertThat(
      content.coerceInside(viewport, targetOffset = Offset.Zero.copy(x = -100f))
    ).isEqualTo(Offset.Zero)

    assertThat(
      content.coerceInside(viewport, targetOffset = Offset.Zero.copy(y = -100f))
    ).isEqualTo(Offset.Zero)

    assertThat(
      content.coerceInside(viewport, targetOffset = Offset(x = -100f, y = -100f))
    ).isEqualTo(Offset.Zero)
  }

  @Test fun `horizontal movement when zoomed content is bigger than viewport in width`() {
    val viewport = Rect(Offset.Zero, Size(1000f, 2000f))
    val contentSize = Size(2000f, 1000f)
    val content = Rect(Offset.Zero, contentSize)

    assertThat(
      content.coerceInside(viewport, targetOffset = Offset.Zero)
    ).isEqualTo(Offset.Zero)

    assertThat(
      content.coerceInside(viewport, targetOffset = Offset(x = -500f, y = 0f))
    ).isEqualTo(Offset(x = -500f, y = 0f))

    assertThat(
      content.coerceInside(viewport, targetOffset = Offset(x = -1000f, y = 0f))
    ).isEqualTo(Offset(x = -1000f, y = 0f))

    // Shouldn't be able to go left any further.
    assertThat(
      content.coerceInside(viewport, targetOffset = Offset(x = -1001f, y = 0f))
    ).isEqualTo(Offset(x = -1000f, y = 0f))

    assertThat(
      Rect(Offset(x = -500f, y = 0f), contentSize).coerceInside(viewport, targetOffset = Offset(x = -501f, y = 0f))
    ).isEqualTo(Offset(x = -500f, y = 0f))

    // Same for the right side.
    assertThat(
      Rect(Offset(x = -1000f, y = 0f), contentSize).coerceInside(viewport, targetOffset = Offset(x = 500f, y = 0f))
    ).isEqualTo(Offset(x = 500f, y = 0f))

    assertThat(
      Rect(Offset(x = -1000f, y = 0f), contentSize).coerceInside(viewport, targetOffset = Offset(x = 1000f, y = 0f))
    ).isEqualTo(Offset(x = 1000f, y = 0f))

    assertThat(
      Rect(Offset(x = -1000f, y = 0f), contentSize).coerceInside(viewport, targetOffset = Offset(x = 1001f, y = 0f))
    ).isEqualTo(Offset(x = 1000f, y = 0f))
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
      Rect(Offset.Zero, contentSize).coerceInside(viewport, targetOffset = Offset.Zero)
    ).isEqualTo(Offset.Zero)

    // When content is at 0,0 it can't be panned R-to-L any further.
    assertThat(
      Rect(Offset.Zero, contentSize).coerceInside(viewport, targetOffset = Offset(x = 10f, 20f))
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset.Zero, contentSize).coerceInside(viewport, targetOffset = Offset(x = -250f, y = -400f))
    ).isEqualTo(Offset(x = -250f, y = -400f))

    assertThat(
      Rect(Offset(x = -500f, y = -300f), contentSize).coerceInside(viewport, targetOffset = Offset(x = -750f, y = -600f))
    ).isEqualTo(Offset(x = -750f, y = -600f))

    assertThat(
      Rect(Offset(x = -1000f, y = -1000f), contentSize).coerceInside(viewport, targetOffset = Offset(x = -1005f, y = -1007f))
    ).isEqualTo(Offset(x = -1000f, y = -1000f))

    assertThat(
      Rect(Offset(x = -500f, y = 0f), contentSize).coerceInside(viewport, Offset(x = -1000f, y = 0f))
    ).isEqualTo(Offset(-1000f, 0f))
  }

  @Test fun `2d movement when zoomed content is bigger than viewport in width`() {
    assertThat(
      Rect(Offset(-80f, 0f), Size(800f, 1300f)).coerceInside(
        viewport = Rect(Offset.Zero, Size(640.0f, 1500.0f)),
        targetOffset = Offset(x = -100f, y = 0f)
      )
    ).isEqualTo(Offset(x = -100f, y = 0f))
  }
}
