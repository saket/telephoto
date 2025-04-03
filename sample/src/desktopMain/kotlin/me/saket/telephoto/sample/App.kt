package me.saket.telephoto.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import me.saket.telephoto.zoomable.CoordinateSpace
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.SpatialOffset
import me.saket.telephoto.zoomable.ZoomableContent
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

fun main() = singleWindowApplication(
  state = WindowState(
    width = 800.dp,
    height = 600.dp,
    position = WindowPosition.Aligned(Alignment.TopStart),
  ),
  title = "telephoto",
) {
  MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
  ) {
    Map(
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
    )
  }
}

@Composable
@OptIn(ExperimentalTelephotoApi::class)
private fun Map(modifier: Modifier = Modifier) {
  // Code adapted from https://github.com/JetBrains/kotlinconf-app
  val painter = rememberSvgPainter(
    if (isSystemInDarkTheme()) "files/ground-floor-dark.svg" else "files/ground-floor.svg"
  )
  val zoomableState = rememberZoomableState(autoApplyTransformations = false).also {
    it.contentScale = ContentScale.Fit
    it.contentAlignment = Alignment.Center
    it.setContentLocation(
      ZoomableContentLocation.unscaledAndTopLeftAligned(painter.intrinsicSize)
    )
  }

  if (painter.intrinsicSize.isSpecified) {
    LaunchedEffect(Unit) {
      zoomableState.zoomTo(
        zoomFactor = zoomableState.zoomSpec.maximum.factor,
        centroid = SpatialOffset(
          offset = Offset(
            x = painter.intrinsicSize.width * 0.30f,
            y = painter.intrinsicSize.height * 0.84f,
          ),
          space = CoordinateSpace.ZoomableContent,
        ),
      )
    }
  }

  Canvas(modifier.zoomable(zoomableState)) {
    val transformation = zoomableState.contentTransformation
    translate(
      left = transformation.offset.x,
      top = transformation.offset.y,
    ) {
      scale(
        scaleX = transformation.scale.scaleX,
        scaleY = transformation.scale.scaleY,
        pivot = Offset(
          x = this.size.width * transformation.transformOrigin.pivotFractionX,
          y = this.size.height * transformation.transformOrigin.pivotFractionY,
        )
      ) {
        painter.run { draw(size) }
      }
    }
  }
}
