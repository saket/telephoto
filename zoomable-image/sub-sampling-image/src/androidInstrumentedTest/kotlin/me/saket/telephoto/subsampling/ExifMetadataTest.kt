package me.saket.telephoto.subsampling

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.runBlocking
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata.ImageOrientation.None
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata.ImageOrientation.Orientation270
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata.ImageOrientation.Orientation90
import org.junit.AssumptionViolatedException
import org.junit.Test

class ExifMetadataTest {
  private val context: Context
    get() = InstrumentationRegistry.getInstrumentation().context

  @Test fun not_rotated_jpg() = runBlocking {
    val metadata = ExifMetadata.read(
      context = context,
      source = SubSamplingImageSource.asset("pahade.jpg")
    )
    assertThat(metadata).isEqualTo(
      ExifMetadata(orientation = None)
    )
  }

  @Test fun rotated_jpgs() = runBlocking {
    assertThat(
      ExifMetadata.read(
        context = context,
        source = SubSamplingImageSource.asset("bellagio_rotated_by_90.jpg")
      )
    ).isEqualTo(
      ExifMetadata(orientation = Orientation90)
    )

    assertThat(
      ExifMetadata.read(
        context = context,
        source = SubSamplingImageSource.asset("bellagio_rotated_by_270.jpg")
      )
    ).isEqualTo(
      ExifMetadata(orientation = Orientation270)
    )
  }

  @Test fun not_rotated_heic() = runBlocking {
    if (SDK_INT < 30) {
      throw AssumptionViolatedException("HEIC files are not supported before API 30.")
    }

    val metadata = ExifMetadata.read(
      context = context,
      source = SubSamplingImageSource.asset("not_rotated_image.heic")
    )
    assertThat(metadata).isEqualTo(
      ExifMetadata(orientation = None)
    )
  }
}
