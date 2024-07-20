package me.saket.telephoto.zoomable.internal

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import me.saket.telephoto.zoomable.ZoomableImage

/** Makes a composable focusable and forwards its focus events to another composable. */
@Stable
internal class FocusForwarder {
  var isParentFocused by mutableStateOf(false)
  var isChildFocused by mutableStateOf(false)
  val childFocusRequester = FocusRequester()

  suspend fun startForwardingFocus() {
    snapshotFlow { isParentFocused }.collect {
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
      .onFocusChanged { forwarder.isParentFocused = it.isFocused }
      // The back button stops working if the parent
      // remains focusable after the child receives focus
      .focusable(enabled = !forwarder.isChildFocused)
  } else {
    this
  }
}

/** Intended for the composable that will receive forwarded focus. */
internal fun Modifier.receiveFocusFrom(forwarder: FocusForwarder): Modifier {
  return this
    .focusRequester(forwarder.childFocusRequester)
    .onFocusChanged { forwarder.isChildFocused = it.isFocused }
    .onAttached { forwarder.startForwardingFocus() }
}
