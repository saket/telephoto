package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

internal val ImageSemanticStateKey = SemanticsPropertyKey<SubSamplingImageSemanticState>("ImageSemanticState")
internal var SemanticsPropertyReceiver.imageSemanticState by ImageSemanticStateKey

internal data class SubSamplingImageSemanticState(
  val isImageDisplayed: Boolean,
  val isImageDisplayedInFullQuality: Boolean,
  val tiles: List<ViewportImageTile>,
)
