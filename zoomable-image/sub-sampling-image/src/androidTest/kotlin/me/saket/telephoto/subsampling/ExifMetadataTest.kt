package me.saket.telephoto.subsampling

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata
import okio.buffer
import okio.source
import org.junit.Assume.assumeTrue
import org.junit.Test

class ExifMetadataTest {
  private val context: Context
    get() = InstrumentationRegistry.getInstrumentation().context

  @Test fun not_rotated_jpg() {
    val metadata = ExifMetadata.read(
      context.assets.open("not_rotated_image.jpg").source().buffer()
    )
    assertThat(metadata).isEqualTo(
      ExifMetadata(
        isFlipped = false,
        rotationDegrees = 0,
      )
    )
  }

  @Test fun rotated_jpg() {
    val metadata = ExifMetadata.read(
      context.assets.open("rotated_image.jpg").source().buffer()
    )
    assertThat(metadata).isEqualTo(
      ExifMetadata(
        isFlipped = false,
        rotationDegrees = 90,
      )
    )
  }

  @Test fun not_rotated_heic() {
    // HEIC files are not supported before API 30.
    assumeTrue(SDK_INT >= 30)

    val metadata = ExifMetadata.read(
      context.assets.open("not_rotated_image.heic").source().buffer()
    )
    assertThat(metadata).isEqualTo(
      ExifMetadata(
        isFlipped = false,
        rotationDegrees = 0,
      )
    )
  }
}
