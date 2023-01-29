package me.saket.telephoto.sample

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.saket.telephoto.zoomable.ZoomableBox
import me.saket.telephoto.zoomable.graphicsLayer
import me.saket.telephoto.zoomable.rememberZoomableState

@OptIn(ExperimentalMaterial3Api::class)
class SampleActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    super.onCreate(savedInstanceState)

    setContent {
      TelephotoTheme {
        Scaffold(
          topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
          }
        ) { contentPadding ->
          Box(Modifier.padding(contentPadding)) {
            val state = rememberZoomableState(
              rotationEnabled = true,
              maxZoomFactor = 1.5f,
            )
            ZoomableBox(
              modifier = Modifier.fillMaxSize(),
              state = state
            ) {
              AsyncImage(
                modifier = Modifier
                  .padding(16.dp)
                  .fillMaxWidth()
                  .height(400.dp)
                  .graphicsLayer(state.transformations)
                  .clip(RoundedCornerShape(8.dp)),
                model = ImageRequest.Builder(LocalContext.current)
                  .data("https://images.unsplash.com/photo-1674560109079-0b1cd708cc2d")
                  .crossfade(true)
                  .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { state.setUnscaledContentSize(it.painter?.intrinsicSize) }
              )
            }
          }
        }
      }
    }
  }
}
