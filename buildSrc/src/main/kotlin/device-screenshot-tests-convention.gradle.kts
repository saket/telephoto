import java.time.Duration

plugins {
  id("com.android.library")
  id("com.dropbox.dropshots")
  id("wtf.emulator.gradle")
}

android {
  defaultConfig {
    // targetSdk version has no effect for libraries. This is only used for
    // the test APK. Workarounds https://issuetracker.google.com/issues/283219177.
    targetSdk = libs.versions.compileSdk.get().toInt()
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
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
  androidTestImplementation(libs.leakcanary.test)
  debugImplementation(libs.leakcanary.core) {
    // Workaround https://github.com/square/leakcanary/pull/2624.
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
  }
}
