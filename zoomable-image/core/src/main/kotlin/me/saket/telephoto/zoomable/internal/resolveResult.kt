package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.graphics.painter.Painter
import me.saket.telephoto.zoomable.ZoomableImageSource
import kotlin.time.Duration

internal fun ZoomableImageSource.ResolveResult.copy(
  delegate: ZoomableImageSource.ImageDelegate? = this.delegate,
  crossfadeDuration: Duration = this.crossfadeDuration,
  placeholder: Painter? = this.placeholder,
) = ZoomableImageSource.ResolveResult(
  delegate = delegate,
  crossfadeDuration = crossfadeDuration,
  placeholder = placeholder,
)
