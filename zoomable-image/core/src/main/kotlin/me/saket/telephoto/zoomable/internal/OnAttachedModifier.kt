package me.saket.telephoto.zoomable.internal

import android.annotation.SuppressLint
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import kotlinx.coroutines.launch

internal fun Modifier.onAttached(callback: suspend () -> Unit): Modifier {
  return this.then(OnAttachedNodeElement(callback))
}

@SuppressLint("ModifierNodeInspectableProperties")
private data class OnAttachedNodeElement(
  private val callback: suspend () -> Unit,
) : ModifierNodeElement<OnAttachedNode>() {

  override fun create() = OnAttachedNode(callback)

  override fun update(node: OnAttachedNode) {
    node.callback = callback
  }
}

@OptIn(ExperimentalComposeUiApi::class)
private class OnAttachedNode(var callback: suspend () -> Unit) : Modifier.Node() {
  override fun onAttach() {
    sideEffect {
      coroutineScope.launch { callback() }
    }
  }
}
