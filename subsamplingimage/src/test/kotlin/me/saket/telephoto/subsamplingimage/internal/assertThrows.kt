package me.saket.telephoto.subsamplingimage.internal

import com.google.common.truth.ThrowableSubject
import com.google.common.truth.Truth

fun assertThrows(action: () -> Unit): ThrowableSubject {
  val result = runCatching(action)
  return Truth.assertThat(result.exceptionOrNull()).apply {
    isNotNull()
  }
}
