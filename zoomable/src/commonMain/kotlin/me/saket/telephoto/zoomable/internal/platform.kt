package me.saket.telephoto.zoomable.internal

internal enum class HostPlatform {
  Android,
  Desktop,
  iOS,
  Wasm
  ;

  companion object;
}

internal expect val HostPlatform.Companion.current: HostPlatform
