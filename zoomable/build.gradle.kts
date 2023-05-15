import org.jetbrains.compose.compose

plugins {
  id("kotlin-multiplatform-library-convention")
  id("published-library-convention")
}

apply(plugin = "kotlin-parcelize")

kotlin {
  sourceSets {
    named("commonMain") {
      dependencies {
        implementation(compose("org.jetbrains.compose.ui:ui-util"))
        implementation(compose.foundation)
        api(libs.compose.foundation)
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
