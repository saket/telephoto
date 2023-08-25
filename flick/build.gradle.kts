import org.jetbrains.compose.compose
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
  id("kotlin-multiplatform-library-convention")
  id("published-library-convention")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
  // https://kotlinlang.org/docs/multiplatform-hierarchy.html#default-hierarchy
  targetHierarchy.default()

  android()
  jvm("desktop")

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
