package me.saket.telephoto.sample

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import coil.Coil
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import com.bumble.appyx.core.integration.NodeHost
import com.bumble.appyx.core.integrationpoint.ActivityIntegrationPoint
import me.saket.telephoto.sample.gallery.MediaAlbum
import me.saket.telephoto.sample.gallery.MediaItem

class SampleActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    super.onCreate(savedInstanceState)

    Coil.setImageLoader(
      ImageLoader.Builder(this)
        .components { add(ImageDecoderDecoder.Factory()) }
        .build()
    )

    val album = MediaAlbum(
      items = listOf(
        // todo: pass ZoomableImageSources instead?
        MediaItem.NormalSizedLocalImage(caption = "GIF"),
        MediaItem.NormalSizedRemoteImage(caption = "Normal sized remote image"),
        MediaItem.SubSampledImage(caption = "Sub sampled image"),
      )
    )
    setContent {
      TelephotoTheme {
        NodeHost(ActivityIntegrationPoint(this, savedInstanceState)) {
          RootNode(
            buildContext = it,
            initialScreen = GalleryScreenKey(album)
          )
        }
      }
    }
  }
}

@Composable
private fun TelephotoTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
    content = content
  )
}
