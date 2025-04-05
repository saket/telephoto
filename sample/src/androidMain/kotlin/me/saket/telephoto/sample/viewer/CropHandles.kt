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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toSize

@Composable
internal fun CropHandles(
  modifier: Modifier = Modifier,
) {
  var viewportSize: Size? by remember {
    mutableStateOf(null)
  }

  // todo: this is ugly code
  val (bounds: Rect?, updateBounds: (Rect?) -> Unit) = viewportSize.let { viewportSize ->
    if (viewportSize == null) {
      remember { mutableStateOf<Rect?>(Rect.Zero) }
    } else {
      LocalDensity.current.run {
        remember {
          mutableStateOf(
            Rect(
              left = 40.dp.toPx(),
              top = 120.dp.toPx(),
              right = viewportSize.width - 40.dp.toPx(),
              bottom = viewportSize.height - 120.dp.toPx(),
            ) as Rect?
          )
        }
      }
    }
  }

  Box(
    modifier
      .fillMaxSize()
      .drawBehind {
        bounds?.let {
          clipRect(
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
            clipOp = ClipOp.Difference,
          ) {
            drawRect(
              color = Color.Black,
              alpha = 0.75f,
            )
          }
        }
      }
      .onSizeChanged {
        viewportSize = it.toSize().takeIf { it.minDimension > 0 }
      }
  ) {
    bounds?.let { bounds ->
      // todo: enforce a minimum size.
      Guidelines(
        modifier = Modifier.matchParentSize(),
        bounds = { bounds },
      )

      Handle(
        modifier = Modifier.offset { bounds.topLeft.round() },
        onDrag = { change ->
          updateBounds(
            bounds.copy(
              top = bounds.top + change.y,
              left = bounds.left + change.x,
            )
          )
        },
      )

      Handle(
        modifier = Modifier.offset { bounds.topRight.round() },
        onDrag = { change ->
          updateBounds(
            bounds.copy(
              top = bounds.top + change.y,
              right = bounds.right + change.x,
            )
          )
        },
      )

      Handle(
        modifier = Modifier.offset { bounds.bottomLeft.round() },
        onDrag = { change ->
          updateBounds(
            bounds.copy(
              bottom = bounds.bottom + change.y,
              left = bounds.left + change.x,
            )
          )
        },
      )

      Handle(
        modifier = Modifier.offset { bounds.bottomRight.round() },
        onDrag = { change ->
          updateBounds(
            bounds.copy(
              bottom = bounds.bottom + change.y,
              right = bounds.right + change.x,
            )
          )
        },
      )
    }
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
  val handleSize = 16.dp
}
