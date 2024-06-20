package me.saket.telephoto.zoomable.internal

internal enum class HostPlatform {
  Android,
  Desktop,
  ;

  companion object;
}

internal expect val HostPlatform.Companion.current: HostPlatform
