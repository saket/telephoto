package me.saket.telephoto.subsampling

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.internal.ExifMetadata
import org.junit.Assume.assumeTrue
import org.junit.Test

class ExifMetadataTest {
  private val context: Context
    get() = InstrumentationRegistry.getInstrumentation().context

  @Test fun not_rotated_jpg() = runBlocking {
    val metadata = ExifMetadata.read(
      context = context,
      source = SubSamplingImageSource.asset("not_rotated_image.jpg")
    )
    assertThat(metadata).isEqualTo(
      ExifMetadata(rotationDegrees = 0)
    )
  }

  @Test fun rotated_jpg() = runBlocking {
    val metadata = ExifMetadata.read(
      context = context,
      source = SubSamplingImageSource.asset("rotated_image.jpg")
    )
    assertThat(metadata).isEqualTo(
      ExifMetadata(rotationDegrees = 90)
    )
  }

  @Test fun not_rotated_heic() = runBlocking {
    // HEIC files are not supported before API 30.
    assumeTrue(SDK_INT >= 30)

    val metadata = ExifMetadata.read(
      context = context,
      source = SubSamplingImageSource.asset("not_rotated_image.heic")
    )
    assertThat(metadata).isEqualTo(
      ExifMetadata(rotationDegrees = 0)
    )
  }
}
