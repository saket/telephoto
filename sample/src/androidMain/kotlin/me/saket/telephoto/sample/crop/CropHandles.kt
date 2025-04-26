package me.saket.telephoto.sample.crop

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.spatial.CoordinateSpace

@Composable
internal fun CropHandles(
  state: CropperState,
  modifier: Modifier = Modifier,
) {
  val backgroundColor = MaterialTheme.colorScheme.background
  Box(
    modifier
      .fillMaxSize()
      .drawBehind {
        state.cropBounds.let { bounds ->
          clipRect(
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
            clipOp = ClipOp.Difference,
          ) {
            drawRect(
              color = backgroundColor,
              alpha = 0.75f,
            )
          }
        }
      }
  ) {
    Guidelines(
      modifier = Modifier.matchParentSize(),
      state = state,
    )

    Handle(
      modifier = Modifier.offset { state.cropBounds.topLeft.round() },
      onDrag = { change ->
        state.updateCropBounds(
          top = state.cropBounds.top + change.y,
          left = state.cropBounds.left + change.x,
        )
      },
    )

    Handle(
      modifier = Modifier.offset { state.cropBounds.topRight.round() },
      onDrag = { change ->
        state.updateCropBounds(
          top = state.cropBounds.top + change.y,
          right = state.cropBounds.right + change.x,
        )
      },
    )

    Handle(
      modifier = Modifier.offset { state.cropBounds.bottomLeft.round() },
      onDrag = { change ->
        state.updateCropBounds(
          bottom = state.cropBounds.bottom + change.y,
          left = state.cropBounds.left + change.x,
        )
      },
    )

    Handle(
      modifier = Modifier.offset { state.cropBounds.bottomRight.round() },
      onDrag = { change ->
        state.updateCropBounds(
          bottom = state.cropBounds.bottom + change.y,
          right = state.cropBounds.right + change.x,
        )
      },
    )
  }
}

@Composable
@OptIn(ExperimentalTelephotoApi::class)
internal fun rememberCropperState(
  imageState: ZoomableImageState,
): CropperState {
  val unscaledContentBounds: Rect = if (imageState.isImageDisplayed) {
    with(imageState.zoomableState.coordinateSystem) {
      unscaledContentBounds.rectIn(CoordinateSpace.Viewport)
    }
  } else {
    Rect.Zero
  }
  val cropBounds = remember(unscaledContentBounds) {
    mutableStateOf(unscaledContentBounds)
  }

  if (imageState.isImageDisplayed) {
    cropBounds.value = cropBounds.value.intersect(
      with(imageState.zoomableState.coordinateSystem) {
        contentBounds.rectIn(CoordinateSpace.Viewport)
      }
    )
  }

  return remember(imageState, cropBounds) {
    CropperState(imageState, cropBounds)
  }.also {
    it.density = LocalDensity.current
  }
}

@Stable
internal class CropperState(
  val imageState: ZoomableImageState,
  private val cropBoundsState: MutableState<Rect>,
) {
  val cropBounds: Rect by cropBoundsState

  internal lateinit var density: Density

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
      cropBoundsState.value = proposedBounds
    }

    // todo: prevent the handles from going outside the viewport's paddings
    //imageState.zoomableState.contentPadding
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
      .background(MaterialTheme.colorScheme.primary, CircleShape)
      .draggable2D(
        state = draggableState,
        interactionSource = interactionSource,
      )
  )
}

@Composable
private fun Guidelines(
  state: CropperState,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Canvas(modifier) {
    val bounds = state.cropBounds
    drawRect(
      topLeft = bounds.topLeft,
      size = bounds.size,
      color = colors.primary,
      style = Stroke(2.dp.toPx()),
    )
  }
}

internal object CropHandles {
  val handleSize = 12.dp
}
