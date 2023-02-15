package me.saket.telephoto.subsamplingimage.internal

import androidx.compose.ui.util.fastForEach

internal inline fun <T, R> List<T>.fastMapNotNull(transform: (T) -> R?): List<R> {
  val target = ArrayList<R>(size)
  fastForEach {
    transform(it)?.let(target::add)
  }
  return target
}
