package me.saket.telephoto.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
internal fun PopupPreview() {
  Box(
    Modifier
      .fillMaxSize()
      .background(Color.Blue)
  )

  Popup(
    properties = PopupProperties(clippingEnabled = false),
  ) {
    Box(
      Modifier
        .fillMaxSize()
    )
  }
}
