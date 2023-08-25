import org.jetbrains.compose.compose

plugins {
  id("kotlin-multiplatform-library-convention")
  id("published-library-convention")
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
  namespace = "me.saket.telephoto.flick"
}
