package me.saket.telephoto.util

import android.graphics.Bitmap
import com.dropbox.differ.Color
import com.dropbox.differ.Image
import com.dropbox.differ.SimpleImageComparator
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat

class BitmapSubject(
  metadata: FailureMetadata,
  private val actual: Bitmap
) : Subject(metadata, actual) {

  companion object {
    fun assertThat(actual: Bitmap): BitmapSubject {
      return Truth
        .assertAbout<BitmapSubject, Bitmap> { metadata, bitmap -> BitmapSubject(metadata, bitmap) }
        .that(actual)
    }
  }

  fun hasSamePixelsAs(expected: Any?) {
    if (expected !is Bitmap) {
      return super.isEqualTo(expected)
    }

    class DifferBitmapImage(val src: Bitmap) : Image {
      override val width: Int get() = src.width
      override val height: Int get() = src.height
      override fun getPixel(x: Int, y: Int) = Color(src.getPixel(x, y))
    }

    val differ = SimpleImageComparator(maxDistance = 0f)
    val diff = differ.compare(
      left = DifferBitmapImage(actual),
      right = DifferBitmapImage(expected),
    )
    assertThat(diff.pixelDifferences).isEqualTo(0)
  }
}
