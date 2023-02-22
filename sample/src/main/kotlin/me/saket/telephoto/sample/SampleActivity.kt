package me.saket.telephoto.sample

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomableViewport
import me.saket.telephoto.zoomable.graphicsLayer
import me.saket.telephoto.zoomable.rememberZoomableViewportState

@OptIn(ExperimentalMaterial3Api::class)
class SampleActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    super.onCreate(savedInstanceState)

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
//            ZoomableViewportSample()
            SubSamplingImageSample()
          }
        }
      }
    }
  }

  @Composable
  private fun ZoomableViewportSample() {
    val state = rememberZoomableViewportState(maxZoomFactor = 1.5f)
    ZoomableViewport(
      modifier = Modifier.fillMaxSize(),
      state = state,
      contentAlignment = Alignment.Center,
      contentScale = ContentScale.Fit,
    ) {
      if (false) {
        AsyncImage(
          modifier = Modifier
            .graphicsLayer(state.contentTransformation)
            .fillMaxSize(),
          model = ImageRequest.Builder(LocalContext.current)
            .data("https://images.unsplash.com/photo-1674560109079-0b1cd708cc2d?w=1500")
            .crossfade(true)
            .build(),
          contentDescription = null,
          // todo: this interferes with ZoomableViewport's content scale.
          //  investigate if this a bug with coil.
          contentScale = ContentScale.Fit,
          onState = {
            state.setContentLocation(ZoomableContentLocation.fitToBoundsAndAlignedToCenter(it.painter?.intrinsicSize))
          }
        )
      } else {
        val painter = painterResource(R.drawable.fox_smol)
        LaunchedEffect(painter) {
          state.setContentLocation(
            ZoomableContentLocation.fitToBoundsAndAlignedToCenter(painter.intrinsicSize)
          )
        }

        Image(
          modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(state.contentTransformation),
          painter = painter,
          contentDescription = null,
          contentScale = ContentScale.Fit,
        )
      }
    }
  }

  @Composable
  private fun SubSamplingImageSample() {
    val state = rememberZoomableViewportState(maxZoomFactor = 1.5f)
    ZoomableViewport(
      state = state,
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.TopEnd,
      contentScale = ContentScale.Crop,
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
