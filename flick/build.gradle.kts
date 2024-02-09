import org.jetbrains.compose.compose
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
  id("kotlin-multiplatform-library-convention")
  id("published-library-convention")
}

kotlin {
  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(compose("org.jetbrains.compose.ui:ui-util"))
        api(compose.foundation)
        api(libs.androidx.annotation)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
      }
    }

    val androidUnitTest by getting {
      apply(plugin = "app.cash.paparazzi")

      dependencies {
        implementation(libs.junit)
        implementation(libs.turbine)
        implementation(libs.molecule.runtime)
        implementation(libs.testParamInjector)
        implementation(libs.compose.ui.material3)
        implementation(libs.kotlinx.coroutines.test)
      }
    }
  }
}

android {
  namespace = "me.saket.telephoto.flick"
}
