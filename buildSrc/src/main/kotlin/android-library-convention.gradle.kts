@file:Suppress("UnstableApiUsage")

plugins {
  id("com.android.library")
  id("kotlin-android")
  id("dev.drewhamilton.poko")
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

  // https://github.com/Kotlin/kotlinx.coroutines/blob/35d88f1ba5bda4d69aa717bf714b88f39a356010/README.md?plain=1#L149-L159
  packaging {
    resources.excludes += "DebugProbesKt.bin"
  }
}

dependencies {
  implementation(libs.compose.runtime)
}
