package me.saket.telephoto.subsamplingimage

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import assertk.assertThat
import assertk.assertions.isEqualTo
import okio.Path.Companion.toPath
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
}
