package me.saket.telephoto.zoomable

import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * Because [ZoomableViewport] handles all gestures including double-taps, `Modifier.clickable()` and
 * related modifiers do not work for composables inside its content hierarchy.
 *
 * Use [ZoomableViewport]'s `onClick` and `onLongClick` parameters instead.
 */
interface BlockClickableModifiers {
  @Deprecated(
    level = DeprecationLevel.ERROR,
    message = "Modifier.clickable() does not work inside ZoomableViewport. Use ZoomableViewport's onClick param instead."
  )
  fun Modifier.clickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
  ): Modifier = noImpl()

  @Deprecated(
    level = DeprecationLevel.ERROR,
    message = "Modifier.clickable() does not work inside ZoomableViewport. Use ZoomableViewport's onClick param instead."
  ) fun Modifier.clickable(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
  ): Modifier = noImpl()

  @Deprecated(
    level = DeprecationLevel.ERROR,
    message = "Modifier.combinedClickable() does not work inside ZoomableViewport. Use ZoomableViewport's onClick and onLongClick params instead."
  )
  fun Modifier.combinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
  ): Modifier = noImpl()

  @Deprecated(
    level = DeprecationLevel.ERROR,
    message = "Modifier.combinedClickable() does not work inside ZoomableViewport. Use ZoomableViewport's onClick and onLongClick params instead."
  )
  fun Modifier.combinedClickable(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
  ): Modifier = noImpl()
}

@Suppress("UnusedReceiverParameter")
private fun Modifier.noImpl(): Nothing =
  throw UnsupportedOperationException("Not implemented, should not be called")
