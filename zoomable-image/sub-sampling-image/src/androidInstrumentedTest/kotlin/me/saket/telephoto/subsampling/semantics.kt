package me.saket.telephoto.subsampling

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import me.saket.telephoto.subsamplingimage.internal.ImageSemanticStateKey
import me.saket.telephoto.subsamplingimage.internal.SubSamplingImageSemanticState
import me.saket.telephoto.subsamplingimage.internal.ViewportImageTile

fun SemanticsNodeInteraction.isImageDisplayed(): Boolean {
  return fetchSemanticsNode().imageSemanticState()?.isImageDisplayed == true
}

internal fun SemanticsNodeInteraction.viewportImageTiles(): List<ViewportImageTile>? {
  return fetchSemanticsNode().imageSemanticState()?.tiles
}

fun SemanticsNodeInteraction.isImageDisplayedInFullQuality(): Boolean {
  return fetchSemanticsNode().imageSemanticState()?.isImageDisplayedInFullQuality == true
}

private fun SemanticsNode.imageSemanticState(): SubSamplingImageSemanticState? {
  return config.getOrNull(ImageSemanticStateKey)
}
