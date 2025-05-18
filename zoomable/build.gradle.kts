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
        api(compose.foundation)
        api(projects.annotations)
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
