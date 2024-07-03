package me.saket.telephoto.zoomable.internal

import android.annotation.SuppressLint
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Stable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.node.ModifierNodeElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Makes a composable focusable and forwards its focus events to another composable.
 */
@Stable
internal class FocusForwarder {
  private val isParentFocused = MutableStateFlow(false)
  val childFocusRequester = FocusRequester()

  fun onParentFocusChanged(focusState: FocusState) {
    check(isParentFocused.tryEmit(focusState.isFocused))
  }

  suspend fun startForwardingFocus() {
    isParentFocused.collect { isParentFocused ->
      if (isParentFocused) {
        childFocusRequester.requestFocus()
      }
    }
  }
}

/** Intended for the composable that will forward focus. */
internal fun Modifier.focusForwarder(forwarder: FocusForwarder, enabled: Boolean): Modifier {
  return if (enabled) {
    this
      .onFocusChanged(forwarder::onParentFocusChanged)
      .focusable()
  } else {
    this
  }
}

/** Intended for the composable that will receive forwarded focus. */
internal fun Modifier.focusable(forwarder: FocusForwarder): Modifier {
  return this
    .focusRequester(forwarder.childFocusRequester)
    .then(OnAttachedNodeElement { forwarder.startForwardingFocus() })
}

@OptIn(ExperimentalComposeUiApi::class)
@SuppressLint("ModifierNodeInspectableProperties")
private data class OnAttachedNodeElement(
  private val callback: suspend () -> Unit,
) : ModifierNodeElement<OnAttachedNodeElement.OnAttachedNode>() {

  override fun create() = OnAttachedNode(callback)

  override fun update(node: OnAttachedNode) {
    node.callback = callback
  }

  class OnAttachedNode(var callback: suspend () -> Unit) : Modifier.Node() {
    override fun onAttach() {
      sideEffect {
        coroutineScope.launch { callback() }
      }
    }
  }
}
