@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.sample

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

class PerformanceTestActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      TelephotoTheme {
        Box(Modifier.fillMaxSize()) {
          val zoomState = rememberZoomableState(
            zoomSpec = ZoomSpec(maxZoomFactor = 2f)
          )
          val imageState = rememberZoomableImageState(zoomState)
          ZoomableAsyncImage(
            state = imageState,
            modifier = Modifier.fillMaxSize(),
            model = "file:///android_asset/progressive_image.jpg",
            contentDescription = "Zoomable image",
          )

          Column(
            Modifier
              .padding(16.dp)
              .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
              .padding(16.dp)
              .align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            if (imageState.subSamplingState?.isImageLoadedInFullQuality == true) {
              Text(
                text = "Tiles loaded",
                color = MaterialTheme.colorScheme.onBackground,
              )
            }
          }
        }
      }
    }
  }
}
