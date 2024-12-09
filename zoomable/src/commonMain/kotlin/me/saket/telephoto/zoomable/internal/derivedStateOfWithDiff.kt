package me.saket.telephoto.zoomable.internal

import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.StateFactoryMarker

@StateFactoryMarker
internal fun <T> derivedStateOfWithDiff(
  calculation: (lastValue: T?) -> T,
): State<T> {
  var lastValue: T? = null
  return androidx.compose.runtime.derivedStateOf {
    calculation(lastValue).also {
      lastValue = it
    }
  }
}
