package me.saket.telephoto.viewport

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.launch
import me.saket.telephoto.viewport.internal.onAllPointersUp

/**
 * @param clipToBounds Defaults to true to act as a reminder that this layout should fill all available
 * space. Otherwise, gestures made outside the viewport's (unscaled) bounds will not be registered.
 */
@Composable
fun ZoomableViewport(
  state: ZoomableViewportState,
  modifier: Modifier = Modifier,
  clipToBounds: Boolean = true,
  contentAlignment: Alignment = Alignment.Center,
  contentScale: ContentScale,
  content: @Composable ZoomableViewportScope.() -> Unit
) {
  SideEffect {
    state.contentScale = contentScale
    state.contentAlignment = contentAlignment
  }

  val zoomableModifier = if (state.isReadyToInteract) {
    val nestedScrollDispatcher = remember { NestedScrollDispatcher() }
    val nestedScrollConnection = remember {
      object : NestedScrollConnection {}
    }

    val scope = rememberCoroutineScope()
    Modifier
      .nestedScroll(nestedScrollConnection, nestedScrollDispatcher)
      .pointerInput(Unit) {
        detectTransformGestures { centroid, panDelta, zoomDelta, rotationDelta ->
          state.onGesture(
            centroid = centroid,
            panDelta = panDelta,
            zoomDelta = zoomDelta,
            rotationDelta = rotationDelta,
            dispatcher = nestedScrollDispatcher,
          )
        }
      }
      .pointerInput(Unit) {
        detectTapGestures(
          onDoubleTap = { centroid ->
            scope.launch {
              state.handleDoubleTapZoomTo(centroidInViewport = centroid)
            }
          }
        )
      }
      .onAllPointersUp {
        // Finish nested scrolling.
        // TODO: integrate this with detectTransformGestures somehow.
        scope.launch {
          nestedScrollDispatcher.dispatchPreFling(available = Velocity.Zero)
          nestedScrollDispatcher.dispatchPostFling(consumed = Velocity.Zero, available = Velocity.Zero)
        }

        // Reset is performed in a new coroutine. The animation will be canceled
        // if TransformableState#transform() is called again by Modifier#transformable(). todo: this doc is outdated now.
        // todo: uncomment this once it no longer cancels double tap to zoom.
        //scope.launch {
        //  state.smoothlySettleOnGestureEnd()
        //}
      }
  } else {
    Modifier
  }

  Box(
    modifier = modifier
      .let { if (clipToBounds) it.clipToBounds() else it }
      .onSizeChanged { state.viewportBounds = Rect(Offset.Zero, size = it.toSize()) }
      .then(zoomableModifier),
    contentAlignment = contentAlignment,
  ) {
    Box(
      modifier = Modifier.onGloballyPositioned { state.contentLayoutBounds = it.boundsInParent() },
      content = {
        val viewportScope = remember(this) { RealZoomableViewportScope(boxScope = this) }
        viewportScope.content()
      }
    )
  }
}

interface ZoomableViewportScope : BoxScope

private class RealZoomableViewportScope(
  val boxScope: BoxScope
) : ZoomableViewportScope, BoxScope by boxScope