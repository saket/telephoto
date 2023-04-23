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
        MediaItem.Image(
          fullSizedUrl = "https://media3.giphy.com/media/v1.Y2lkPTc5MGI3NjExYTAxMWYwZDk5N2NlYTM1MWZmZDJhMTNlZmQ2ODZmN2Q0NGYwYjRiMiZjdD1n/OJNNOaOJx8AgWFXZui/giphy.gif",
          placeholderImageUrl = "https://media3.giphy.com/media/v1.Y2lkPTc5MGI3NjExYTAxMWYwZDk5N2NlYTM1MWZmZDJhMTNlZmQ2ODZmN2Q0NGYwYjRiMiZjdD1n/OJNNOaOJx8AgWFXZui/giphy.gif",
          caption = "Happy good morning",
        ),
        MediaItem.Image(
          fullSizedUrl = "https://live.staticflickr.com/65535/46217553745_fa38e0e7f0_o_d.jpg",
          placeholderImageUrl = "https://live.staticflickr.com/65535/46217553745_e8d9242548_w_d.jpg",
          caption = "Follow the light",
        ),
        MediaItem.Image(
          fullSizedUrl = "https://live.staticflickr.com/2809/11679312514_3f759b77cd_o_d.jpg",
          placeholderImageUrl = "https://live.staticflickr.com/2809/11679312514_7592396e9f_w_d.jpg",
          caption = "Flamingo",
        ),
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
