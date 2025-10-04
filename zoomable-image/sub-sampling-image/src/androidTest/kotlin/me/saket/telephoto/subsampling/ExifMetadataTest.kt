package me.saket.telephoto.subsampling

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata.ImageOrientation.None
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata.ImageOrientation.Orientation180
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata.ImageOrientation.Orientation270
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata.ImageOrientation.Orientation90
import org.junit.AssumptionViolatedException
import org.junit.Test

class ExifMetadataTest {
  private val context: Context
    get() = InstrumentationRegistry.getInstrumentation().context

  @Test fun not_rotated_jpg() = runTest {
    val metadata = ExifMetadata.read(
      context = context,
      source = SubSamplingImageSource.asset("pahade.jpg")
    )
    assertThat(metadata).isEqualTo(
      ExifMetadata(orientation = None, flippedHorizontally = false)
    )
  }

  @Test fun rotated_jpgs() = runTest {
    assertThat(
      ExifMetadata.read(
        context = context,
        source = SubSamplingImageSource.asset("jasper_rotated_90.jpg")
      )
    ).isEqualTo(
      ExifMetadata(orientation = Orientation90, flippedHorizontally = false)
    )

    assertThat(
      ExifMetadata.read(
        context = context,
        source = SubSamplingImageSource.asset("jasper_rotated_270.jpg")
      )
    ).isEqualTo(
      ExifMetadata(orientation = Orientation270, flippedHorizontally = false)
    )
  }

  @Test fun flipped_jpgs() = runTest {
    assertThat(
      ExifMetadata.read(
        context = context,
        source = SubSamplingImageSource.asset("jasper_flipped_horizontally.jpg")
      )
    ).isEqualTo(
      ExifMetadata(orientation = None, flippedHorizontally = true)
    )
    assertThat(
      ExifMetadata.read(
        context = context,
        source = SubSamplingImageSource.asset("jasper_flipped_vertically.jpg")
      )
    ).isEqualTo(
      ExifMetadata(orientation = Orientation180, flippedHorizontally = true)
    )
  }

  @Test fun not_rotated_heic() = runTest {
    if (SDK_INT < 30) {
      throw AssumptionViolatedException("HEIC files are not supported before API 30.")
    }

    val metadata = ExifMetadata.read(
      context = context,
      source = SubSamplingImageSource.asset("not_rotated_image.heic")
    )
    assertThat(metadata).isEqualTo(
      ExifMetadata(orientation = None, flippedHorizontally = false)
    )
  }
}
