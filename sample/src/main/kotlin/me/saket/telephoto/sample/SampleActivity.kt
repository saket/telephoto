package me.saket.telephoto.sample

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.SubsamplingScaleImageView
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomableViewport
import me.saket.telephoto.zoomable.graphicsLayer
import me.saket.telephoto.zoomable.rememberZoomableState

@OptIn(ExperimentalMaterial3Api::class)
class SampleActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    WindowCompat.setDecorFitsSystemWindows(window, true)
    super.onCreate(savedInstanceState)

    if (false) {
      setContentView(
        SubsamplingScaleImageView(this).also {
          it.setImage(ImageSource.asset("pahade.jpeg"))
        }
      )
    } else {
      setContent {
        TelephotoTheme {
          Scaffold(
            topBar = {
              TopAppBar(title = { Text(stringResource(R.string.app_name)) })
            }
          ) { contentPadding ->
            Box(Modifier.padding(contentPadding)) {
              //ZoomableViewportSample()
              SubSamplingImageSample()
            }
          }
        }
      }
    }
  }

  @Composable
  private fun ZoomableViewportSample() {
    val state = rememberZoomableState(
      rotationEnabled = false,
      maxZoomFactor = 1.5f,
    )
    ZoomableViewport(
      modifier = Modifier.fillMaxSize(),
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

  @Composable
  private fun SubSamplingImageSample() {
    val state = rememberZoomableState(
      rotationEnabled = false,
      maxZoomFactor = 1.5f,
    )
    ZoomableViewport(
      modifier = Modifier
        .padding(vertical = 40.dp, horizontal = 120.dp)
        .fillMaxSize(),
      state = state,
      clipToBounds = false
    ) {
      SubSamplingImage(
        modifier = Modifier
          .fillMaxWidth()
          .height(300.dp)
          .background(MaterialTheme.colorScheme.tertiaryContainer),
        state = rememberSubSamplingImageState(
          zoomableState = state,
          imageSource = ImageSource.asset("pahade.jpeg")
        )
      )
    }
  }
}
