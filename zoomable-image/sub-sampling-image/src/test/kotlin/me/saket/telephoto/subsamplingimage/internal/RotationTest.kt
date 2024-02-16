package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class RotationTest {
  @Test fun rotate_entire_image() {
    IntRect(
      topLeft = IntOffset.Zero,
      bottomRight = IntOffset(x = 4032, y = 3024)
    ).let { original ->
      assertThat(original.rotateBy(90, unRotatedParent = original)).isEqualTo(
        IntRect(
          topLeft = IntOffset.Zero,
          bottomRight = IntOffset(x = 3024, y = 4032)
        )
      )
    }

    IntRect(
      topLeft = IntOffset.Zero,
      bottomRight = IntOffset(x = 3024, y = 4032)
    ).let { original ->
      assertThat(original.rotateBy(-90, unRotatedParent = original)).isEqualTo(
        IntRect(
          topLeft = IntOffset.Zero,
          bottomRight = IntOffset(x = 4032, y = 3024)
        )
      )
    }

    IntRect(
      topLeft = IntOffset.Zero,
      bottomRight = IntOffset(x = 3024, y = 4032),
    ).let { original ->
      assertThat(original.rotateBy(-90, original)).isEqualTo(
        IntRect(
          topLeft = IntOffset.Zero,
          bottomRight = IntOffset(x = 4032, y = 3024),
        )
      )
    }
  }

  @Test fun rotate_entire_image_2() {
    val original = IntRect(
      topLeft = IntOffset.Zero,
      bottomRight = IntOffset(x = 1600, y = 800)
    )

    assertThat(original.rotateBy(90, unRotatedParent = original)).isEqualTo(
      IntRect(
        topLeft = IntOffset.Zero,
        bottomRight = IntOffset(x = 800, y = 1600)
      )
    )
  }

  @Test fun rotate_parts_of_an_image() {
    val landscape = IntRect(
      topLeft = IntOffset.Zero,
      bottomRight = IntOffset(x = 1600, y = 800)
    )

    assertThat(
      IntRect(
        topLeft = landscape.topCenter,
        bottomRight = landscape.centerRight,
      ).rotateBy(90, landscape)
    ).isEqualTo(
      IntRect(
        topLeft = IntOffset(x = 400, y = 800),
        bottomRight = IntOffset(x = 800, y = 1600),
      )
    )
    assertThat(
      IntRect(
        topLeft = landscape.topCenter,
        bottomRight = IntOffset(x = 1200, y = 400),
      ).rotateBy(180, landscape)
    ).isEqualTo(
      IntRect(
        topLeft = IntOffset(x = 400, y = 400),
        bottomRight = IntOffset(x = 800, y = 800),
      )
    )
    assertThat(
      IntRect(
        offset = IntOffset(x = 400, y = 400),
        size = IntSize(400, 400)
      ).rotateBy(270, landscape)
    ).isEqualTo(
      IntRect(
        topLeft = IntOffset(x = 400, y = 800),
        bottomRight = IntOffset(x = 800, y = 1200),
      )
    )
    assertThat(
      IntRect(
        topLeft = landscape.topCenter,
        bottomRight = landscape.centerRight,
      )
        .rotateBy(90, landscape)
        .rotateBy(90, landscape)
        .rotateBy(90, landscape)
        .rotateBy(90, landscape)
    ).isEqualTo(
      IntRect(
        topLeft = landscape.topCenter,
        bottomRight = landscape.centerRight,
      )
    )

    val portrait = IntRect(
      topLeft = IntOffset.Zero,
      bottomRight = IntOffset(x = 800, y = 1600)
    )
    val bottomLeftRect = IntRect(
      topLeft = portrait.centerLeft,
      bottomRight = portrait.bottomCenter,
    )
    assertThat(bottomLeftRect.rotateBy(-90, portrait)).isEqualTo(
      IntRect(
        topLeft = IntOffset(x = 800, y = 400),
        bottomRight = IntOffset(x = 1600, y = 800)
      )
    )

    IntRect(
      topLeft = IntOffset.Zero,
      bottomRight = IntOffset(x = 5, y = 6)
    ).let { parent ->
      val rect = IntRect(
        topLeft = IntOffset(1, 2),
        bottomRight = IntOffset(2, 4),
      )

      assertThat(rect.rotateBy(-90, parent)).isEqualTo(
        IntRect(
          topLeft = IntOffset(2, 3),
          bottomRight = IntOffset(4, 4),
        )
      )
    }
  }
}
