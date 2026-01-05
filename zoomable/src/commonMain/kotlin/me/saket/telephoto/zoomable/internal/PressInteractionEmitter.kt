package me.saket.telephoto.zoomable.internal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Emits [PressInteraction] events to `interactionSource`. Runs using [PointerEventPass.Initial] to
 * see events before gesture handlers can consume them, and does not consume any events itself.
 *
 * This is implemented as a standalone modifier because `Modifier.zoomable` handles various kinds of
 * gestures in two separate (delegated) modifier nodes. Coordinating press interactions between them
 * would have been tricky.
 */
internal data class PressInteractionElement(
  private val interactionSource: MutableInteractionSource?,
) : ModifierNodeElement<PressInteractionNode>() {

  override fun create(): PressInteractionNode {
    return PressInteractionNode(interactionSource)
  }

  override fun update(node: PressInteractionNode) {
    node.update(interactionSource)
  }
}

internal class PressInteractionNode(
  private var interactionSource: MutableInteractionSource?,
) : DelegatingNode() {

  private val pointerInputNode = delegate(SuspendingPointerInputModifierNode {
    val source = interactionSource ?: return@SuspendingPointerInputModifierNode
    coroutineScope {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val press = PressInteraction.Press(down.position)
        launch { source.emit(press) }

        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
        if (up != null) {
          launch { source.emit(PressInteraction.Release(press)) }
        } else {
          launch { source.emit(PressInteraction.Cancel(press)) }
        }
      }
    }
  })

  fun update(interactionSource: MutableInteractionSource?) {
    if (this.interactionSource != interactionSource) {
      this.interactionSource = interactionSource
      pointerInputNode.resetPointerInputHandler()
    }
  }
}
