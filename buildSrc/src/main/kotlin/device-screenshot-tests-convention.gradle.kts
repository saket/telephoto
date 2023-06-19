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
  devices.set(
    listOf(
      mapOf("model" to "Pixel7Atd", "version" to 31)
    )
  )
  directoriesToPull.set(listOf("/sdcard/Download/"))
  numUniformShards.set(3)
  numFlakyTestAttempts.set(2) // 3 runs in total.
  fileCacheTtl.set(Duration.ofDays(30))
  timeout.set(Duration.ofMinutes(15)) // Note to self: this is per shard and not per test.
  printOutput.set(true) // Print report URL even for successful test runs.
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

// androidx.compose.runtime frequently runs into ANRs when compositions
// need to be disposed at the end of instrumented tests.
if (libs.versions.compose.runtime.get().startsWith("1.4")) {
  configurations.configureEach {
    if (name.contains("test", ignoreCase = true)) {
      resolutionStrategy.eachDependency {
        if (requested.module == libs.compose.runtime.get().module) {
          useVersion("1.5.0-beta01")
        }
      }
    }
  }
} else {
  throw RuntimeException("no longer needed.")
}
