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
  composeOptions.kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
}

dependencies {
  implementation(libs.compose.runtime)
}
