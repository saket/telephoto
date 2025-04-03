@file:Suppress("PackageDirectoryMismatch")  // todo: move this to :annotations?

package me.saket.telephoto

@RequiresOptIn(
  message = "This telephoto API is experimental and can receive breaking changes in the future.",
  level = RequiresOptIn.Level.ERROR,
)
@Retention(value = AnnotationRetention.BINARY)
annotation class ExperimentalTelephotoApi
