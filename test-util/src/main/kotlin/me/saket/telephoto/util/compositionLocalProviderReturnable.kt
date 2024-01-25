package me.saket.telephoto.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.currentComposer

@Composable
@OptIn(InternalComposeApi::class)
fun <T> compositionLocalProviderReturnable(
  vararg values: ProvidedValue<*>,
  content: @Composable () -> T
): T {
  currentComposer.startProviders(values)
  val t = content()
  currentComposer.endProviders()
  return t
}
