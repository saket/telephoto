plugins {
  id("me.saket.android.application")
  id("me.saket.kotlin.multiplatform")
  id("me.saket.compose")
  alias(libs.plugins.kotlin.parcelize)
}

kotlin {
  androidTarget()
  jvm("desktop")

  sourceSets {
    commonMain.dependencies {
      implementation(projects.zoomable)
      implementation(projects.flick)

      implementation(compose.foundation)
      implementation(compose.components.resources)

      implementation(libs.circuit.runtime)
      implementation(libs.circuit.backstack)
    }

    androidMain.dependencies {
      implementation(projects.zoomableImage.coil)
      implementation(projects.zoomableImage.glide)
      implementation(projects.zoomablePeekOverlay)

      implementation(libs.androidx.appcompat)
      implementation(libs.androidx.ktx.core)
      implementation(libs.androidx.ktx.palette)
      implementation(libs.androidx.activity)
      implementation(libs.compose.ui.material3)
      implementation(libs.coil.compose)
      implementation(libs.coil.gif)
    }

    val desktopMain by getting
    desktopMain.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(compose.material3)
    }
  }
}

android {
  namespace = "me.saket.telephoto.sample"

  defaultConfig {
    applicationId = namespace
    minSdk = 31
    compileSdk = libs.versions.compileSdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
  }
  signingConfigs {
    create("release") {
      keyAlias = "sample"
      keyPassword = "frappe-snivel-possible-downward"
      storeFile = file("release_keystore.jks")
      storePassword = "abstract-emperor-john-twill"
    }
  }
  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      signingConfig = signingConfigs.getByName("debug")
    }
    create("benchmark") {
      initWith(getByName("release"))
      matchingFallbacks += listOf("release")
      isDebuggable = false
    }
  }
  lint {
    lintConfig = file("lint.xml")
    abortOnError = System.getenv("CI") == null
  }
  buildFeatures {
    buildConfig = true
  }
}
