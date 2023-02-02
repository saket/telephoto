package me.saket.telephoto.sample

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
              rotationEnabled = false,
              maxZoomFactor = 1.5f,
            )
            ZoomableBox(
              modifier = Modifier
                //.padding(80.dp)
                .fillMaxSize()
                /*.border(1.dp, Color.Yellow)*/,
              state = state,
              clipToBounds = false
            ) {
              AsyncImage(
                modifier = Modifier
                  .fillMaxWidth()
                  .wrapContentHeight()
                  .graphicsLayer(state.contentTransformations),
                model = ImageRequest.Builder(LocalContext.current)
                  .data("https://images.unsplash.com/photo-1674560109079-0b1cd708cc2d")
                  .crossfade(true)
                  .build(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                onState = { state.setUnscaledContentSize(it.painter?.intrinsicSize) }
              )
            }
          }
        }
      }
    }
  }
}
