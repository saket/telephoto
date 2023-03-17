package me.saket.telephoto.subsamplingimage.internal

import me.saket.telephoto.subsamplingimage.ImageSource
import me.saket.telephoto.subsamplingimage.StreamingImageSource
import java.io.IOException

internal fun IOException.withImprovedMessageFor(imageSource: ImageSource): IOException {
  return if (imageSource is StreamingImageSource && message?.contains("format not supported") == true) {
    IOException(
      """
      |Failed to read image. Make sure that your ImageSource.stream()'s producer lambda isn't returning the same value on each call.
      |
      |Good:
      |ImageSource.stream { fileSystem.source(file) }
      |
      |Bad:
      |val source = fileSystem.source(file)
      |ImageSource.stream { source }
      |
      |""".trimMargin(),
      /* cause = */ this
    )
  } else this
}
