@file:Suppress("UnstableApiUsage")

plugins {
  id("com.android.library")
  id("kotlin-android")
}

android {
  compileSdk = 33
  defaultConfig {
    minSdk = 24
    resourcePrefix = "_telephoto"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
    freeCompilerArgs += listOf(
      "-Xcontext-receivers",
      "-Xjvm-default=all",
    )
  }
  lint.abortOnError = true
  buildFeatures.compose = true
  composeOptions.kotlinCompilerExtensionVersion = "1.4.4"
}
