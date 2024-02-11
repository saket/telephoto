package me.saket.telephoto.subsamplingimage

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha

@SuppressLint("ComposeParameterOrder")
@Deprecated("", level = DeprecationLevel.HIDDEN)  // For binary compatibility.
@Composable
fun SubSamplingImage(
  state: SubSamplingImageState,
  modifier: Modifier = Modifier,
  contentDescription: String?,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
) {
  SubSamplingImage(
    state,
    contentDescription,
    modifier,
    alpha,
    colorFilter
  )
}
