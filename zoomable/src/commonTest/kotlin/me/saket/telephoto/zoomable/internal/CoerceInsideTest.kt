package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.assertEquals
import kotlin.test.Test

class CoerceInsideTest {
  @Test fun `no zoom`() {
    val layoutSize = Size(1000f, 2000f)
    val contentSize = Size(1000f, 500f)

    assertEquals(
      Offset.Zero,
      Rect(Offset.Zero, contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    assertEquals(
      Offset.Zero,
      Rect(Offset.Zero.copy(x = 100f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    assertEquals(
      Offset.Zero,
      Rect(Offset.Zero.copy(y = 100f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    assertEquals(
      Offset.Zero,
      Rect(Offset(x = 100f, y = 100f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    assertEquals(
      Offset.Zero,
      Rect(Offset.Zero.copy(x = -100f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    assertEquals(
      Offset.Zero,
      Rect(Offset.Zero.copy(y = -100f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    assertEquals(
      Offset.Zero,
      Rect(Offset(x = -100f, y = -100f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )
  }

  @Test fun `horizontal movement when zoomed content is bigger than layout size in width`() {
    val layoutSize = Size(1000f, 2000f)
    val contentSize = Size(2000f, 1000f)

    assertEquals(
      Offset.Zero,
      Rect(Offset.Zero, contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    assertEquals(
      Offset(x = -500f, y = 0f),
      Rect(Offset(x = -500f, y = 0f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    assertEquals(
      Offset(x = -1000f, y = 0f),
      Rect(Offset(x = -1000f, y = 0f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    // Shouldn't be able to go left any further.
    assertEquals(
      Offset(x = -1000f, y = 0f),
      Rect(Offset(x = -1001f, y = 0f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    // Same for the right side.
    assertEquals(
      Offset(x = 0f, y = 0f),
      Rect(Offset(x = 500f, y = 0f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )
  }

  @Test fun `vertical movement when zoomed content is bigger than layout size in height`() {
    val layoutSize = Rect(Offset.Zero, Size(1000f, 2000f))
    val contentSize = Size(1000f, 3000f)

    // todo.
  }

  @Test fun `2d movement when zoomed content is bigger than layout size in both width and height`() {
    val layoutSize = Size(1000f, 2000f)
    val contentSize = Size(2000f, 3000f)

    assertEquals(
      Offset.Zero,
      Rect(Offset.Zero, contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    // When content is at 0,0 it can't be panned R-to-L any further.
    assertEquals(
      Offset.Zero,
      Rect(Offset(x = 10f, 20f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    assertEquals(
      Offset(x = -250f, y = -400f),
      Rect(Offset(x = -250f, y = -400f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    assertEquals(
      Offset(x = -750f, y = -600f),
      Rect(Offset(x = -750f, y = -600f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    assertEquals(
      Offset(x = -1000f, y = -1000f),
      Rect(Offset(x = -1005f, y = -1007f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )

    assertEquals(
      Offset(-1000f, 0f),
      Rect(Offset(x = -1000f, y = 0f), contentSize).topLeftCoercedInside(layoutSize, Alignment.TopCenter)
    )
  }

  @Test
  fun `2d movement when zoomed content is bigger than layout size in width`() {
    assertEquals(
      Offset(x = -100f, y = 0f),
      Rect(Offset(x = -100f, y = 0f), Size(800f, 1300f)).topLeftCoercedInside(
        destination = Size(640.0f, 1500.0f),
        alignment = Alignment.TopCenter
      )
    )
  }
}

private fun Rect.topLeftCoercedInside(destination: Size, alignment: Alignment): Offset {
  return calculateTopLeftToOverlapWith(
    destination = destination,
    alignment = alignment,
    layoutDirection = LayoutDirection.Ltr
  )
}
