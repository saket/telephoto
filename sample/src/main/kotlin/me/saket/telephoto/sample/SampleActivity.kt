package me.saket.telephoto.sample

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import me.saket.telephoto.zoomable.rememberZoomableViewportState

@OptIn(ExperimentalMaterial3Api::class)
class SampleActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    super.onCreate(savedInstanceState)

    if (false) {
      setContentView(
        SubsamplingScaleImageView(this).also {
          it.layoutParams =
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
          it.setImage(ImageSource.asset("smol.jpg"))
          it.setBackgroundColor(Color.DarkGray.toArgb())
        }
      )
    } else {
      setContent {
        TelephotoTheme {
          Scaffold(
            topBar = {
              TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                  containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp)
                )
              )
            }
          ) { contentPadding ->
            Box(Modifier.padding(contentPadding)) {
//              ZoomableViewportSample()
              SubSamplingImageSample()
            }
          }
        }
      }
    }
  }

  @Composable
  private fun ZoomableViewportSample() {
    val state = rememberZoomableViewportState(
      rotationEnabled = false,
      maxZoomFactor = 1.5f,
    )
    ZoomableViewport(
      modifier = Modifier.fillMaxSize(),
      state = state,
      contentAlignment = Alignment.TopCenter,
    ) {
      AsyncImage(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .graphicsLayer(state.contentTransformations),
        model = ImageRequest.Builder(LocalContext.current)
          .data("https://images.unsplash.com/photo-1674560109079-0b1cd708cc2d?w=1500")
          .crossfade(true)
          .build(),
        contentDescription = null,
        // todo: this interferes with ZoomableViewport's content scale.
        //  investigate if this a bug with coil.
        //contentScale = ContentScale.Crop,
        onState = { state.setUnscaledContentSize(it.painter?.intrinsicSize) })
    }
  }

  @Composable
  private fun SubSamplingImageSample() {
    val state = rememberZoomableViewportState(
      rotationEnabled = false,
      maxZoomFactor = 1.5f,
    )

    ZoomableViewport(
      state = state,
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.TopCenter
    ) {
      SubSamplingImage(
        modifier = Modifier.fillMaxSize(),
        state = rememberSubSamplingImageState(
          viewportState = state,
          imageSource = ImageSource.asset("path.jpg"),
        ),
        contentDescription = null,
      )
    }
  }
}
