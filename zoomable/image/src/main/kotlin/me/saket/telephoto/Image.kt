package me.saket.telephoto

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomableViewportState
import me.saket.telephoto.zoomable.applyTransformation

@Composable
fun Image(
  painter: Painter,
  zoomState: ZoomableViewportState,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null
) {
  LaunchedEffect(painter.intrinsicSize) {
    zoomState.setContentLocation(
      ZoomableContentLocation.fitInsideAndCenterAligned(painter.intrinsicSize)
    )
  }

  Image(
    modifier = modifier
      .fillMaxSize()
      .applyTransformation(zoomState.contentTransformation),
    painter = painter,
    contentDescription = contentDescription,
    alignment = Alignment.Center,
    contentScale = ContentScale.Inside,
    alpha = alpha,
    colorFilter = colorFilter,
  )
}

@Composable
fun Image(
  assetName: String,
  zoomState: ZoomableViewportState,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null
) {
  val canBeSubSampled = remember(assetName) {
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
      assetName.substringAfterLast(".", missingDelimiterValue = "")
    )
    mimeType in ImageSource.supportedMimeTypes()
  }

  // Reading of assets is faster using ImageSource.asset() so skip coil whenever possible.
  if (canBeSubSampled) {
    SubSamplingImage(
      state = rememberSubSamplingImageState(
        imageSource = ImageSource.asset(assetName),
        viewportState = zoomState
      ),
      modifier = modifier,
      contentDescription = contentDescription,
      alpha = alpha,
      colorFilter = colorFilter,
    )
  } else {
    Image(
      imageSource = ZoomableImageSource.coil(
        model = Uri.parse(
          "${ContentResolver.SCHEME_FILE}:///android_asset/$assetName"
        )
      ),
      zoomState = zoomState,
      contentDescription = contentDescription,
      modifier = modifier,
      alpha = alpha,
      colorFilter = colorFilter
    )
  }
}
