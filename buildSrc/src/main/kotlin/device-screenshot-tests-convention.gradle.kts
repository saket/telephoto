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
  version.set(libs.versions.emulatorWtf.cli.get())
  devices.set(
    listOf(
      mapOf("model" to "Pixel7Atd", "version" to 31)
    )
  )
  directoriesToPull.set(listOf("/sdcard/Download/"))
  numUniformShards.set(3)
  numFlakyTestAttempts.set(2) // 3 runs in total.
  fileCacheTtl.set(Duration.ofDays(30))
}

dependencies {
  androidTestImplementation(libs.androidx.test.ktx)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.compose.ui.test.junit)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.testParamInjector)
  debugImplementation(libs.compose.ui.test.activityManifest)
}
