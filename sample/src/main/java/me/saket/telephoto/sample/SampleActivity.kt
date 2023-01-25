package me.saket.telephoto.sample

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
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
            val state = rememberZoomableState()
            ZoomableBox(state = state) {
              AsyncImage(
                modifier = Modifier
                  .graphicsLayer(state.transformations)
                  .fillMaxWidth()
                  .height(400.dp)
                  .padding(16.dp)
                  .clip(RoundedCornerShape(8.dp)),
                model = "https://images.unsplash.com/photo-1674560109079-0b1cd708cc2d?w=1000",
                contentDescription = null,
                contentScale = ContentScale.Crop,
              )
            }
          }
        }
      }
    }
  }
}
