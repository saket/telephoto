package me.saket.telephoto.util

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.currentComposer

@Composable
@OptIn(InternalComposeApi::class)
@SuppressLint("ComposableNaming")
fun <T> CompositionLocalProviderReturnable(
  vararg values: ProvidedValue<*>,
  content: @Composable () -> T
): T {
  currentComposer.startProviders(values)
  val t = content()
  currentComposer.endProviders()
  return t
}
