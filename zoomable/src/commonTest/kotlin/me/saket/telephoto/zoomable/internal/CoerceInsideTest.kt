package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class CoerceInsideTest {
  @Test fun `no zoom in viewport starting at zero`() {
    val viewportBounds = Rect(Offset.Zero, Size(1000f, 2000f))
    val contentSize = Size(1000f, 500f)

    assertThat(
      Rect(Offset.Zero, contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset.Zero.copy(x = 100f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset.Zero.copy(y = 100f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset(x = 100f, y = 100f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset.Zero.copy(x = -100f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset.Zero.copy(y = -100f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset(x = -100f, y = -100f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)
  }

  @Test fun `no zoom in padded viewport`() {
    val viewportBounds = Rect(Offset(200f, 300f), Size(1000f, 2000f))
    val contentSize = Size(1000f, 500f)

    assertThat(
      Rect(Offset.Zero, contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset(200f, 300f))

    assertThat(
      Rect(viewportBounds.topLeft, contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(viewportBounds.topLeft)

    assertThat(
      Rect(viewportBounds.topLeft + Offset(1f, 1f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(viewportBounds.topLeft)
  }

  @Test fun `horizontal movement when zoomed content is bigger than viewport size in width`() {
    val viewportBounds = Rect(Offset.Zero, Size(1000f, 2000f))
    val contentSize = Size(2000f, 1000f)

    // Allowed horizontal range: [viewport.right - contentWidth, viewport.left] = [1000 - 2000, 0] = [-1000, 0]
    assertThat(
      Rect(Offset.Zero, contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset(x = -500f, y = 0f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -500f, y = 0f))

    assertThat(
      Rect(Offset(x = -1000f, y = 0f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -1000f, y = 0f))

    // Shouldn't be able to go left any further.
    assertThat(
      Rect(Offset(x = -1001f, y = 0f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -1000f, y = 0f))

    // Same for the right side.
    assertThat(
      Rect(Offset(x = 500f, y = 0f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset(x = 0f, y = 0f))
  }

  @Test fun `horizontal movement when zoomed content is bigger than padded viewport size in width`() {
    val viewportBounds = Rect(Offset(100f, 200f), Size(1000f, 2000f))
    val contentSize = Size(2000f, 1000f)

    // For a padded viewport the allowed horizontal range becomes:
    // [viewport.right - contentWidth, viewport.left]
    // = [(100+1000 - 2000), 100]
    // = [-900, 100]

    // An offset of -500 is within the allowed range.
    assertThat(
      Rect(Offset(-500f, 200f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset(-500f, 200f))

    // If the rect is too far left, then it gets clamped to -900.
    assertThat(
      Rect(Offset(-1000f, 200f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset(-900f, 200f))

    // An offset too far to the right, e.g., 200, should be clamped to the viewport’s left, 100.
    assertThat(
      Rect(Offset(200f, 200f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset(100f, 200f))
  }

  @Test fun `2d movement when zoomed content is bigger than viewport size in both width and height`() {
    val viewportBounds = Rect(Offset.Zero, Size(1000f, 2000f))
    val contentSize = Size(2000f, 3000f)

    assertThat(
      Rect(Offset.Zero, contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    // When content is at 0,0 it can't be panned R-to-L any further.
    assertThat(
      Rect(Offset(x = 10f, 20f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset.Zero)

    assertThat(
      Rect(Offset(x = -250f, y = -400f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -250f, y = -400f))

    assertThat(
      Rect(Offset(x = -750f, y = -600f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -750f, y = -600f))

    assertThat(
      Rect(Offset(x = -1005f, y = -1007f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset(x = -1000f, y = -1000f))

    assertThat(
      Rect(Offset(x = -1000f, y = 0f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset(-1000f, 0f))
  }

  @Test fun `2d movement when zoomed content is bigger than padded viewport size in both width and height`() {
    val viewportBounds = Rect(Offset(50f, 75f), Size(1000f, 2000f))
    val contentSize = Size(2000f, 3000f)

    // Allowed range:
    //  horizontal: [viewport.right - contentWidth, viewport.left]
    //  = [(50+1000-2000), 50]
    //  = [-950, 50]
    //
    //  vertical: [viewport.bottom - contentHeight, viewport.top]
    //  = [(75+2000-3000), 75]
    //  = [-925, 75]

    // A vertical offset that is too far down is clamped to the maximum allowed.
    assertThat(
      Rect(Offset(50f, 100f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset(50f, 75f))

    // Both horizontal and vertical offsets are out of bounds.
    assertThat(
      Rect(Offset(-1000f, -1000f), contentSize).topLeftCoercedInside(viewportBounds, Alignment.TopCenter)
    ).isEqualTo(Offset(-950f, -925f))
  }

  @Test fun `2d movement when zoomed content is bigger than viewport size in width`() {
    assertThat(
      Rect(Offset(x = -100f, y = 0f), Size(800f, 1300f)).topLeftCoercedInside(
        viewportBounds = Rect(Offset.Zero, Size(640.0f, 1500.0f)),
        alignment = Alignment.TopCenter
      )
    ).isEqualTo(Offset(x = -100f, y = 0f))
  }
}

private fun Rect.topLeftCoercedInside(viewportBounds: Rect, alignment: Alignment): Offset {
  return calculateTopLeftToOverlapWith(
    viewportBounds = viewportBounds,
    alignment = alignment,
    layoutDirection = LayoutDirection.Ltr,
  )
}
