package me.saket.telephoto.sample

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.Coil
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import me.saket.telephoto.sample.gallery.MediaAlbum
import me.saket.telephoto.sample.gallery.MediaItem

class SampleActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    setupImmersiveMode()
    super.onCreate(savedInstanceState)

    Coil.setImageLoader(
      ImageLoader.Builder(this)
        .components { add(ImageDecoderDecoder.Factory()) }
        .build()
    )

    val album = MediaAlbum(
      items = listOf(
        // Photo by Anita Austvika on https://unsplash.com/photos/yFxAORZcJQk.
        MediaItem.Image(
          fullSizedUrl = "https://images.unsplash.com/photo-1678465952838-c9d7f5daaa65",
          placeholderImageUrl = "https://images.unsplash.com/photo-1678465952838-c9d7f5daaa65?w=100",
          caption = "Breakfast",
        ),
        // Photos by Romain Guy on https://www.flickr.com/photos/romainguy/.
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
        MediaItem.Image(
          fullSizedUrl = "https://live.staticflickr.com/6024/5911366388_600e7e6734_o_d.jpg",
          placeholderImageUrl = "https://i.imgur.com/bQtqkj6.jpg",
          caption = "Sierra Sunset",
        ),
      )
    )
    setContent {
      val systemUiController = rememberSystemUiController()
      val useDarkIcons = !isSystemInDarkTheme()
      LaunchedEffect(systemUiController, useDarkIcons) {
        systemUiController.setSystemBarsColor(
          color = Color.Transparent,
          darkIcons = useDarkIcons
        )
      }

      TelephotoTheme {
        Navigation(
          initialScreenKey = GalleryScreenKey(album)
        )
      }
    }
  }

  private fun setupImmersiveMode() {
    // Draw behind display cutouts.
    window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

    // No scrim behind transparent navigation bar.
    window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

    // System bars use fade by default to hide/show. Make them slide instead.
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
