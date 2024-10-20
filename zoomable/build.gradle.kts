import org.jetbrains.compose.compose

plugins {
  id("me.saket.android.library")
  id("me.saket.library.publishing")
  id("me.saket.kotlin.multiplatform")
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.paparazzi)
}

kotlin {
  sourceSets {
    named("commonMain") {
      dependencies {
        implementation(compose("org.jetbrains.compose.ui:ui-util"))
        api(compose.foundation)
        api(libs.androidx.annotation)
      }
    }

    named("commonTest") {
      dependencies {
        implementation(kotlin("test"))
      }
    }
  }
}

android {
  namespace = "me.saket.telephoto.zoomable"
}
