package me.saket.telephoto.zoomable

@RequiresOptIn(
  message = "This telephoto API is experimental and can receive breaking changes in the future.",
  level = RequiresOptIn.Level.ERROR,
)
@Retention(value = AnnotationRetention.BINARY)
annotation class ExperimentalTelephotoApi
