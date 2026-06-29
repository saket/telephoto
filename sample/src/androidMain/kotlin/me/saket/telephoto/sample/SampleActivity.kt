package me.saket.telephoto.sample

import android.os.Bundle
import android.os.StrictMode
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.Coil
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import me.saket.telephoto.sample.gallery.MediaAlbum
import me.saket.telephoto.sample.gallery.MediaItem
import java.util.concurrent.Executor

class SampleActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    if (BuildConfig.DEBUG) {
      enableStrictMode()
    }
    enableEdgeToEdge()
    setupImmersiveMode()
    super.onCreate(savedInstanceState)

    Coil.setImageLoader(
      ImageLoader.Builder(this)
        .components { add(ImageDecoderDecoder.Factory()) }
        .build()
    )

    val issue165Reproducer = intent.getBooleanExtra(ExtraIssue165Reproducer, false) ||
      intent.data?.let { it.scheme == "telephoto" && it.host == "issue-165" } == true
    val issue165ImageLoader = intent.issue165ImageLoader()
    val album = if (issue165Reproducer) issue165ReproducerAlbum() else sampleAlbum()
    val initialScreenKey = if (issue165Reproducer) {
      MediaViewerScreenKey(album, initialIndex = 0, imageLoader = issue165ImageLoader)
    } else {
      GalleryScreenKey(album)
    }
    setContent {
      TelephotoTheme {
        Navigation(
          initialScreenKey = initialScreenKey
        )
      }
    }
  }

  private fun enableStrictMode() {
    StrictMode.setThreadPolicy(
      StrictMode.ThreadPolicy.Builder()
        .detectAll()
        .penaltyDeath()
        .build()
    )
    StrictMode.setVmPolicy(
      StrictMode.VmPolicy.Builder()
        .detectLeakedClosableObjects()
        .penaltyListener(Executor(Runnable::run)) {
          // https://github.com/aosp-mirror/platform_frameworks_base/commit/e7ae30f76788bcec4457c4e0b0c9cbff2cf892f3
          if (!it.stackTraceToString().contains("sun.nio.fs.UnixSecureDirectoryStream.finalize")) {
            throw it
          }
        }
        .build()
    )
  }

  private fun setupImmersiveMode() {
    // Draw behind display cutouts.
    window.attributes.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

    // No scrim behind transparent navigation bar.
    window.setFlags(FLAG_LAYOUT_NO_LIMITS, FLAG_LAYOUT_NO_LIMITS)

    // System bars use fade by default to hide/show. Make them slide instead.
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
  }
}

private const val ExtraIssue165Reproducer = "me.saket.telephoto.sample.ISSUE_165_REPRODUCER"
private const val ExtraIssue165ImageLoader = "me.saket.telephoto.sample.ISSUE_165_IMAGE_LOADER"

private fun android.content.Intent.issue165ImageLoader(): ImageLoaderVariant {
  val loader = getStringExtra(ExtraIssue165ImageLoader)
    ?: data?.getQueryParameter("loader")
  return when (loader?.lowercase()) {
    "coil3" -> ImageLoaderVariant.Coil3
    else -> ImageLoaderVariant.Coil2
  }
}

private fun sampleAlbum() = MediaAlbum(
  items = listOf(
    // Photo by Mahyar Motebassem (https://unsplash.com/photos/f0d83M-PkNw).
    MediaItem.Image(
      fullSizedUrl = "https://unsplash.com/photos/f0d83M-PkNw/download?ixid=M3wxMjA3fDB8MXxhbGx8fHx8fHx8fHwxNzQ3ODMzODU2fA&force=true",
      placeholderImageUrl = "https://unsplash.com/photos/f0d83M-PkNw/download?ixid=M3wxMjA3fDB8MXxhbGx8fHx8fHx8fHwxNzQ3ODMzODU2fA&force=true&w=300",
      caption = "Breakfast",
      aspectRatio = 300f / 375f,
    ),
    // Photo by Jack White (https://unsplash.com/photos/jDCIBr88RGU/).
    MediaItem.Image(
      fullSizedUrl = "https://unsplash.com/photos/L_SjEwDtJEI/download?ixid=M3wxMjA3fDB8MXxhbGx8fHx8fHx8fHwxNzQ3ODM0MjAxfA&force=true",
      placeholderImageUrl = "https://unsplash.com/photos/L_SjEwDtJEI/download?ixid=M3wxMjA3fDB8MXxhbGx8fHx8fHx8fHwxNzQ3ODM0MjAxfA&force=true&w=300",
      caption = "Porsche 912",
      aspectRatio = 300f / 450f,
    ),
    // Photo by Romain Guy (https://www.flickr.com/photos/romainguy/).
    MediaItem.Image(
      fullSizedUrl = "https://live.staticflickr.com/4734/39442725251_be4b6395a2_o_d.jpg",
      placeholderImageUrl = "https://live.staticflickr.com/4734/39442725251_ed2353237e_c_d.jpg",
      caption = "Long Sight",
      aspectRatio = 533f / 800f
    ),
    MediaItem.Image(
      fullSizedUrl = "https://live.staticflickr.com/4687/39511378181_e815b89822_o_d.jpg",
      placeholderImageUrl = "https://live.staticflickr.com/4687/39511378181_ab0c158858_c_d.jpg",
      caption = "Follow",
      aspectRatio = 449 / 800f,
    ),
  )
)

private fun issue165ReproducerAlbum() = MediaAlbum(
  items = listOf(
    MediaItem.Image(
      fullSizedUrl = "file:///android_asset/issue165/25mb.jpg",
      placeholderImageUrl = "https://unsplash.com/photos/f0d83M-PkNw/download?ixid=M3wxMjA3fDB8MXxhbGx8fHx8fHx8fHwxNzQ3ODMzODU2fA&force=true&w=300",
      caption = "Breakfast",
      aspectRatio = 300f / 375f,
    ),
    MediaItem.Image(
      fullSizedUrl = "file:///android_asset/issue165/50mb.jpg",
      placeholderImageUrl = "https://unsplash.com/photos/L_SjEwDtJEI/download?ixid=M3wxMjA3fDB8MXxhbGx8fHx8fHx8fHwxNzQ3ODM0MjAxfA&force=true&w=300",
      caption = "Porsche 912",
      aspectRatio = 300f / 450f,
    ),
    MediaItem.Image(
      fullSizedUrl = "file:///android_asset/issue165/25mb.jpg",
      placeholderImageUrl = "https://live.staticflickr.com/4734/39442725251_ed2353237e_c_d.jpg",
      caption = "Long Sight",
      aspectRatio = 533f / 800f
    ),
    MediaItem.Image(
      fullSizedUrl = "file:///android_asset/issue165/50mb.jpg",
      placeholderImageUrl = "https://live.staticflickr.com/4687/39511378181_ab0c158858_c_d.jpg",
      caption = "Follow",
      aspectRatio = 449 / 800f,
    ),
  )
)

@Composable
internal fun TelephotoTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
    content = content
  )
}
