package me.saket.telephoto.subsamplingimage

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.test.runTest
import me.saket.telephoto.subsamplingimage.util.exists
import okio.Buffer
import okio.ForwardingSource
import okio.Path.Companion.toPath
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.FilterInputStream

@RunWith(RobolectricTestRunner::class)
class SubSamplingImageSourceTest {
  @Test fun `parse valid resource content uris`() {
    val preview = ImageBitmap(1, 1)
    val uri = Uri.parse("android.resource://me.saket.telephoto/123")
    val source = SubSamplingImageSource.contentUri(uri, preview)
    assertThat(source).isEqualTo(ResourceImageSource(123, preview))
  }

  @Test fun `parse invalid resource content uris`() {
    val preview = ImageBitmap(1, 1)
    val uri = Uri.parse("android.resource://me.saket.telephoto/abc")
    val source = SubSamplingImageSource.contentUri(uri, preview)
    assertThat(source).isEqualTo(UriImageSource(uri, preview))
  }

  @Test fun `parse file uris without schemes`() {
    val uri = Uri.parse("/sdcard0/download/foo.png")
    val source = SubSamplingImageSource.contentUri(uri)
    assertThat(source).isEqualTo(
      FileImageSource(
        path = "/sdcard0/download/foo.png".toPath(),
        preview = null,
        onClose = null,
      )
    )
  }

  @Test fun `parse file uris with schemes`() {
    val uri = Uri.parse("file:///sdcard0/download/foo.png")
    val source = SubSamplingImageSource.contentUri(uri)
    assertThat(source).isEqualTo(
      FileImageSource(
        path = "/sdcard0/download/foo.png".toPath(),
        preview = null,
        onClose = null,
      )
    )
  }

  @Test fun `parse asset uris`() {
    val uri = Uri.parse("file:///android_asset/foo/bar.png")
    val source = SubSamplingImageSource.contentUri(uri)
    assertThat(source).isEqualTo(AssetImageSource(AssetPath("foo/bar.png"), preview = null))
  }

  @Test fun `exists closes the stream opened by peek`() = runTest {
    val context = RuntimeEnvironment.getApplication()
    val uri = Uri.parse("content://me.saket.telephoto/image")
    var closed = false
    val stream = object : FilterInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3))) {
      override fun close() {
        closed = true
        super.close()
      }
    }
    shadowOf(context.contentResolver).registerInputStream(uri, stream)

    val source = SubSamplingImageSource.contentUri(uri)
    assertThat(source.exists(context)).isTrue()
    assertThat(closed).isTrue()
  }

  @Test fun `exists does not close underlying rawSource`() = runTest {
    val context = RuntimeEnvironment.getApplication()
    var closed = false
    val rawSource = object : ForwardingSource(Buffer().write(byteArrayOf(1, 2, 3))) {
      override fun close() {
        closed = true
        super.close()
      }
    }
    val source = SubSamplingImageSource.rawSource({ rawSource }) as RawImageSource

    assertThat(source.exists(context)).isTrue()
    assertThat(closed).isFalse()
    assertThat(source.peek(context).use { it.readByteArray() }).isEqualTo(byteArrayOf(1, 2, 3))

    source.close()
    assertThat(closed).isTrue()
  }
}
