@file:Suppress("UnstableApiUsage")

plugins {
  id("com.android.library")
  id("kotlin-android")
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
    resourcePrefix = "_telephoto"
  }
  lint.abortOnError = true
  buildFeatures.compose = true
  composeOptions.kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
}

dependencies {
  implementation(libs.compose.runtime)
}
