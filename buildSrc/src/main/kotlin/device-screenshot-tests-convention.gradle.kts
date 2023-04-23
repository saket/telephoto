import gradle.kotlin.dsl.accessors._71f190358cebd46a469f2989484fd643.android
import gradle.kotlin.dsl.accessors._71f190358cebd46a469f2989484fd643.androidTestImplementation
import gradle.kotlin.dsl.accessors._71f190358cebd46a469f2989484fd643.debugImplementation
import java.time.Duration

plugins {
  id("com.android.library")
  id("com.dropbox.dropshots")
  id("wtf.emulator.gradle")
}

android {
  defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  testOptions.animationsDisabled = true
}

emulatorwtf {
  version.set("0.9.8")
  devices.set(
    listOf(
      mapOf("model" to "Pixel7Atd", "version" to 31)
    )
  )
  directoriesToPull.set(listOf("/sdcard/Download/"))
  numUniformShards.set(3)
  numFlakyTestAttempts.set(1)
  fileCacheTtl.set(Duration.ofDays(7))
}

dependencies {
  androidTestImplementation("androidx.test:core-ktx:1.5.0")
  androidTestImplementation("androidx.test:rules:1.5.0")
  androidTestImplementation("androidx.test.ext:junit-ktx:1.1.5")
  androidTestImplementation("androidx.compose.ui:ui-test-junit4:$1.4.0")
  androidTestImplementation("com.google.truth:truth:1.1.3")
  androidTestImplementation("com.google.testparameterinjector:test-parameter-injector:1.11")
  debugImplementation("androidx.compose.ui:ui-test-manifest:1.4.0")
}
