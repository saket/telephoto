@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.zoomable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.flow.filter
import me.saket.telephoto.subsamplingimage.RealSubSamplingImageState
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.SubSamplingImageState
import me.saket.telephoto.subsamplingimage.contentDescription
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.internal.FocusForwarder
import me.saket.telephoto.zoomable.internal.PlaceholderBoundsProvider
import me.saket.telephoto.zoomable.internal.applyBaseTransformation
import me.saket.telephoto.zoomable.internal.focusForwarder
import me.saket.telephoto.zoomable.internal.receiveFocusFrom

/**
 * A _drop-in_ replacement for async `Image()` composables featuring support for pan & zoom gestures
 * and automatic sub-sampling of large images. This ensures that images maintain their intricate details
 * even when fully zoomed in, without causing any `OutOfMemory` exceptions.
 *
 * Because `Modifier.zoomable()` consumes all gestures including double-taps, [Modifier.clickable]
 * and [Modifier.combinedClickable] will not work on this composable. As an alternative, [onClick]
 * and [onLongClick] parameters can be used instead.
 *
 * @param clipToBounds defaults to true to act as a reminder that this layout should probably fill all
 * available space. Otherwise, gestures made outside the composable's layout bounds will not be registered.
 */
@Composable
fun ZoomableImage(
  image: ZoomableImageSource,
  contentDescription: String?,
  gestures: EnabledZoomGestures,
  modifier: Modifier = Modifier,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  onDoubleClick: DoubleClickToZoomListener = DoubleClickToZoomListener.cycle(),
  clipToBounds: Boolean = true,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  interactionSource: MutableInteractionSource? = null,
) {
  state.zoomableState.also {
    it.contentAlignment = alignment
    it.contentScale = contentScale
    it.contentPadding = contentPadding
  }

  var canvasSize by remember { mutableStateOf(Size.Unspecified) }
  val resolved = key(image) {
    image.resolve(
      canvasSize = remember {
        snapshotFlow { canvasSize }.filter { it.isSpecified && !it.isEmpty() }
      }
    )
  }

  // When ZoomableImage() is focused, the actual image underneath might not be displayed yet or
  // might change if the image source updates. Forward focus events to the active image so that
  // it can receive key events for detecting keyboard shortcuts.
  val focusForwarder = remember { FocusForwarder() }

  Box(
    modifier = modifier
      .onSizeChanged { canvasSize = it.toSize() }
      .focusForwarder(focusForwarder, enabled = state.hardwareShortcutsEnabled())
      .semantics(mergeDescendants = true) { this.isTraversalGroup = true }
      .contentDescriptionIfImageIsEmpty(state, contentDescription),
    propagateMinConstraints = true,
  ) {
    state.isImageDisplayed = when (resolved.delegate) {
      is ZoomableImageSource.PainterDelegate -> resolved.delegate.painter != null
      is ZoomableImageSource.SubSamplingDelegate -> state.subSamplingState?.isImageDisplayed ?: false
      else -> false
    }

    val animatedAlpha by if (LocalInspectionMode.current) {
      remember { mutableFloatStateOf(1f) }
    } else {
      animateFloatAsState(
        targetValue = if (state.isImageDisplayed) 1f else 0f,
        animationSpec = tween(resolved.crossfadeDurationMs),
        label = "Crossfade animation",
      )
    }

    state.isPlaceholderDisplayed = resolved.placeholder != null && animatedAlpha < 1f

    // If a state restoration happened and the image was previously zoomed in, the placeholder will
    // no longer be aligned correctly and can't be displayed anymore. It'd be nice if the placeholder
    // can also be kept in sync with the zoomable state, but that's a hard problem to solve.
    var wasImageZoomedIn by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(state) {
      onDispose {
        // Note to self: it is important that this value update happens only after
        // the composition is disposed. Otherwise the placeholder will not be
        // displayed for images that are displayed in full-zoom by default.
        wasImageZoomedIn = state.zoomableState.zoomFraction.let { it != null && it > 0f }
      }
    }

    if (state.isPlaceholderDisplayed && !wasImageZoomedIn) {
      val painter = animatedPainter(resolved.placeholder!!)

      // The placeholder image uses a separate ZoomableState so that it
      // can swallow all zoom gestures while the full image is loading.
      // TODO: Make placeholders zoomable and smoothly transition to full
      //  image without losing zoom level https://github.com/saket/telephoto/issues/104
      val placeholderZoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f, overzoomEffect = OverzoomEffect.Disabled),
        hardwareShortcutsSpec = HardwareShortcutsSpec.Disabled,
        // Handle gestures, but ignore their transformations. This will prevent
        // FlickToDismiss() (and other gesture containers) from accidentally dismissing
        // this image when a quick-zoom gesture is made before the image is fully loaded.
        autoApplyTransformations = false,
      ).also {
        it.contentScale = contentScale
        it.contentAlignment = alignment
        it.contentPadding = contentPadding
        it.setContentLocation(ZoomableContentLocation.scaledInsideAndCenterAligned(painter.intrinsicSize))
      }
      val boundsProvider = PlaceholderBoundsProvider(placeholderZoomableState)
      DisposableEffect(state, boundsProvider) {
        state.realZoomableState.placeholderBoundsProvider = boundsProvider
        onDispose {
          state.realZoomableState.placeholderBoundsProvider = null
        }
      }
      Image(
        modifier = Modifier
          .zoomable(
            state = placeholderZoomableState,
            onClick = onClick,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
            clipToBounds = clipToBounds,
          )
          .applyBaseTransformation(placeholderZoomableState),
        painter = painter,
        contentDescription = null,
        alignment = Alignment.Center,
        contentScale = ContentScale.Inside,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }

    val zoomable = Modifier
      .receiveFocusFrom(focusForwarder)
      .zoomable(
        state = state.zoomableState,
        gestures = if (state.isPlaceholderDisplayed) EnabledZoomGestures.None else gestures,
        onClick = onClick,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        clipToBounds = clipToBounds,
        interactionSource = interactionSource,
      )

    when (val delegate = resolved.delegate) {
      null -> {
        Box(Modifier)
      }

      is ZoomableImageSource.PainterDelegate -> {
        state.zoomableState.autoApplyTransformations = true
        state.zoomableState.setContentLocation(
          ZoomableContentLocation.scaledInsideAndCenterAligned(delegate.painter?.intrinsicSize)
        )
        Image(
          modifier = zoomable,
          painter = animatedPainter(delegate.painter ?: EmptyPainter),
          contentDescription = contentDescription,
          alignment = Alignment.Center,
          contentScale = ContentScale.Inside,
          alpha = alpha * animatedAlpha,
          colorFilter = colorFilter,
        )
      }

      is ZoomableImageSource.SubSamplingDelegate -> {
        val subSamplingState = rememberSubSamplingImageState(
          imageSource = delegate.source,
          zoomableState = state.zoomableState,
          imageOptions = delegate.imageOptions
        ).also {
          it.asReal().preferConsistentTileSize = false
        }
        DisposableEffect(state, subSamplingState) {
          state.subSamplingState = subSamplingState
          onDispose {
            state.subSamplingState = null
          }
        }
        SubSamplingImage(
          modifier = zoomable,
          state = subSamplingState,
          contentDescription = contentDescription,
          alpha = alpha * animatedAlpha,
          colorFilter = colorFilter,
        )
      }
    }
  }
}

@Composable
fun ZoomableImage(
  image: ZoomableImageSource,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
  onDoubleClick: DoubleClickToZoomListener = DoubleClickToZoomListener.cycle(),
  contentPadding: PaddingValues = PaddingValues(0.dp),
) {
  ZoomableImage(
    image = image,
    contentDescription = contentDescription,
    modifier = modifier,
    state = state,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    gestures = EnabledZoomGestures.ZoomAndPan,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
    contentPadding = contentPadding,
  )
}

@Deprecated(
  "Use the 'gestures' parameter instead. " +
    "Replace `gesturesEnabled = true` with `gestures = ZoomInteractions.ZoomAndPan`, " +
    "or `gesturesEnabled = false` with `gestures = ZoomInteractions.None`.",
)
@Composable
fun ZoomableImage(
  image: ZoomableImageSource,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  gesturesEnabled: Boolean = true,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
  onDoubleClick: DoubleClickToZoomListener = DoubleClickToZoomListener.cycle(),
  contentPadding: PaddingValues = PaddingValues(0.dp),
) {
  ZoomableImage(
    image = image,
    contentDescription = contentDescription,
    modifier = modifier,
    state = state,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    gestures = if (gesturesEnabled) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
    contentPadding = contentPadding,
  )
}

private fun Modifier.contentDescriptionIfImageIsEmpty(
  imageState: ZoomableImageState,
  contentDescription: String?
): Modifier {
  return if (imageState.isImageDisplayed) {
    // The full image composables -- Image() or SubSamplingImage(),
    // have their own content descriptions.
    this
  } else {
    this.contentDescription(contentDescription)
  }
}

@Composable
@Suppress("unused")
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
fun ZoomableImage(
  image: ZoomableImageSource,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  gesturesEnabled: Boolean = true,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
  onDoubleClick: DoubleClickToZoomListener = DoubleClickToZoomListener.cycle(),
) {
  ZoomableImage(
    image = image,
    contentDescription = contentDescription,
    modifier = modifier,
    state = state,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    gestures = if (gesturesEnabled) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
  )
}

@Composable
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
fun ZoomableImage(
  image: ZoomableImageSource,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  gesturesEnabled: Boolean = true,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
) {
  ZoomableImage(
    image = image,
    contentDescription = contentDescription,
    modifier = modifier,
    state = state,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    gestures = if (gesturesEnabled) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = DoubleClickToZoomListener.cycle(),
    clipToBounds = clipToBounds,
  )
}

@Composable
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
fun ZoomableImage(
  image: ZoomableImageSource,
  contentDescription: String?,
  gestures: EnabledZoomGestures,
  modifier: Modifier = Modifier,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  onDoubleClick: DoubleClickToZoomListener = DoubleClickToZoomListener.cycle(),
  clipToBounds: Boolean = true,
  contentPadding: PaddingValues = PaddingValues(0.dp),
) {
  ZoomableImage(
    image = image,
    contentDescription = contentDescription,
    gestures = gestures,
    modifier = modifier,
    state = state,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    clipToBounds = clipToBounds,
    contentPadding = contentPadding,
    interactionSource = null,
  )
}

@Composable
private fun animatedPainter(painter: Painter): Painter {
  if (painter is RememberObserver) {
    // Animated painters use RememberObserver's APIs
    // for starting & stopping their animations.
    return remember(painter) { painter }
  }
  return painter
}

private object EmptyPainter : Painter() {
  override val intrinsicSize: Size get() = Size.Unspecified
  override fun DrawScope.onDraw() = Unit
}

private val ZoomableImageSource.ResolveResult.crossfadeDurationMs: Int
  get() = crossfadeDuration.inWholeMilliseconds.toInt()

private val ZoomableImageState.realZoomableState: RealZoomableState
  get() = zoomableState as RealZoomableState  // Safe because ZoomableState is a sealed type.

private fun SubSamplingImageState.asReal(): RealSubSamplingImageState =
  this as RealSubSamplingImageState  // Safe because SubSamplingImageState is a sealed type.

private fun ZoomableImageState.hardwareShortcutsEnabled(): Boolean {
  return realZoomableState.hardwareShortcutsSpec.enabled
}
