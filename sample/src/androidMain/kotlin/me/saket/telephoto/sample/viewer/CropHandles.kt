package me.saket.telephoto.sample.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import me.saket.telephoto.zoomable.ZoomableImageState

@Composable
internal fun CropHandles(
  imageState: ZoomableImageState,
  modifier: Modifier = Modifier,
) {
  val contentBounds = imageState.zoomableState.contentBounds
  var cropBounds by remember(contentBounds) {
    mutableStateOf(contentBounds)
  }
  cropBounds = cropBounds.intersect(
    imageState.zoomableState.transformedContentBounds
  )

  val density = LocalDensity.current

  fun updateCropBounds(
    left: Float = cropBounds.left,
    top: Float = cropBounds.top,
    right: Float = cropBounds.right,
    bottom: Float = cropBounds.bottom,
  ) {
    val minSizePx = with(density) {
      (CropHandles.handleSize * 4).toPx()
    }
    val proposedBounds = Rect(
      left = left,
      top = top,
      right = right,
      bottom = bottom,
    )
    if (!proposedBounds.isEmpty && proposedBounds.size.minDimension > minSizePx) {
      cropBounds = proposedBounds
    }
  }

  Box(
    modifier
      .fillMaxSize()
      .drawBehind {
        clipRect(
          left = cropBounds.left,
          top = cropBounds.top,
          right = cropBounds.right,
          bottom = cropBounds.bottom,
          clipOp = ClipOp.Difference,
        ) {
          drawRect(
            color = Color.Black,
            alpha = 0.5f,
          )
        }
      }
  ) {
    Guidelines(
      modifier = Modifier.matchParentSize(),
      bounds = { cropBounds },
    )

    Handle(
      modifier = Modifier.offset { cropBounds.topLeft.round() },
      onDrag = { change ->
        updateCropBounds(
          top = cropBounds.top + change.y,
          left = cropBounds.left + change.x,
        )
      },
    )

    Handle(
      modifier = Modifier.offset { cropBounds.topRight.round() },
      onDrag = { change ->
        updateCropBounds(
          top = cropBounds.top + change.y,
          right = cropBounds.right + change.x,
        )
      },
    )

    Handle(
      modifier = Modifier.offset { cropBounds.bottomLeft.round() },
      onDrag = { change ->
        updateCropBounds(
          bottom = cropBounds.bottom + change.y,
          left = cropBounds.left + change.x,
        )
      },
    )

    Handle(
      modifier = Modifier.offset { cropBounds.bottomRight.round() },
      onDrag = { change ->
        updateCropBounds(
          bottom = cropBounds.bottom + change.y,
          right = cropBounds.right + change.x,
        )
      },
    )
  }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Handle(
  onDrag: (Offset) -> Unit,
  modifier: Modifier = Modifier,
) {
  val draggableState = rememberDraggable2DState(onDrag)
  val interactionSource = remember { MutableInteractionSource() }

  val ripplePadding = 8.dp
  val size = CropHandles.handleSize

  Box(
    modifier
      // todo: get rid of the size modifier by drawing the ripple and laying out the handle manually?
      .offset(
        // Center the handle at the handle's offset.
        x = -ripplePadding - size / 2,
        y = -ripplePadding - size / 2,
      )
      .clip(CircleShape)
      .clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = {},
      )
      .padding(ripplePadding)
      .size(size)
      .background(Color.White, CircleShape)
      .draggable2D(
        state = draggableState,
        interactionSource = interactionSource,
      )
  )
}

@Composable
private fun Guidelines(
  bounds: () -> Rect,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier) {
    // Left line.
    drawLine(
      start = bounds().topLeft + Offset(x = 0f, y = CropHandles.handleSize.toPx()),
      end = bounds().bottomLeft - Offset(x = 0f, y = CropHandles.handleSize.toPx()),
      color = Color.White,
      strokeWidth = 2.dp.toPx(),
      cap = StrokeCap.Round,
    )

    // Top line.
    drawLine(
      start = bounds().topLeft + Offset(x = CropHandles.handleSize.toPx(), y = 0f),
      end = bounds().topRight - Offset(x = CropHandles.handleSize.toPx(), y = 0f),
      color = Color.White,
      strokeWidth = 2.dp.toPx(),
      cap = StrokeCap.Round,
    )

    // Right line.
    drawLine(
      start = bounds().topRight + Offset(x = 0f, y = CropHandles.handleSize.toPx()),
      end = bounds().bottomRight - Offset(x = 0f, y = CropHandles.handleSize.toPx()),
      color = Color.White,
      strokeWidth = 2.dp.toPx(),
      cap = StrokeCap.Round,
    )

    // Bottom line.
    drawLine(
      start = bounds().bottomLeft + Offset(x = CropHandles.handleSize.toPx(), y = 0f),
      end = bounds().bottomRight - Offset(x = CropHandles.handleSize.toPx(), y = 0f),
      color = Color.White,
      strokeWidth = 2.dp.toPx(),
      cap = StrokeCap.Round,
    )
  }
}

object CropHandles {
  val handleSize = 12.dp
}
