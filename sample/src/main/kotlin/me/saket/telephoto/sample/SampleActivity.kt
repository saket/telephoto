package me.saket.telephoto.sample

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
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
    WindowCompat.setDecorFitsSystemWindows(window, false)
    super.onCreate(savedInstanceState)

    if (false) {
      setContentView(
        SubsamplingScaleImageView(this).also {
          it.layoutParams =
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
          it.setImage(ImageSource.asset("pahade.jpeg"))
          it.setBackgroundColor(Color.DarkGray.toArgb())
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
          .graphicsLayer(state.contentTransformations)
          .fillMaxWidth()
          .wrapContentHeight(),
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
        .fillMaxSize()
        .padding(vertical = 40.dp, horizontal = 120.dp)
        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
        .borderOverContent(2.dp, Color.White, cornerRadius = 8.dp),
      state = state,
      clipToBounds = false
    ) {
      SubSamplingImage(
        modifier = Modifier
          .fillMaxWidth()
          //.wrapContentHeight()
          .height(300.dp)
          .border(2.dp, Color.Yellow),
        state = rememberSubSamplingImageState(
          zoomableState = state,
          imageSource = ImageSource.asset("pahade.jpeg")
        )
      )
    }
  }
}

private fun Modifier.borderOverContent(width: Dp, color: Color, cornerRadius: Dp): Modifier {
  return drawWithContent {
    val cornerRadiusPx = cornerRadius.toPx()
    val borderPx = width.toPx()

    drawContent()
    drawRoundRect(
      color = color,
      cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
      style = Stroke(borderPx)
    )
  }
}
