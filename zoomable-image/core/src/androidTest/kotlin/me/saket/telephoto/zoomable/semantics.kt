@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.zoomable

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import me.saket.telephoto.subsamplingimage.internal.ImageSemanticStateKey
import me.saket.telephoto.subsamplingimage.internal.SubSamplingImageSemanticState

// Note to self: this can only be used for sub-sampled images right now.
fun SemanticsNodeInteraction.isImageDisplayed(): Boolean {
  return fetchSemanticsNode().subSamplingImageSemanticState()?.isImageDisplayed == true
}

// Note to self: this can only be used for sub-sampled images right now.
fun SemanticsNodeInteraction.isImageDisplayedInFullQuality(): Boolean {
  return fetchSemanticsNode().subSamplingImageSemanticState()?.isImageDisplayedInFullQuality == true
}

private fun SemanticsNode.subSamplingImageSemanticState(): SubSamplingImageSemanticState? {
  return config.getOrNull(ImageSemanticStateKey)
}
