import org.jetbrains.compose.compose

plugins {
  id("me.saket.android.library")
  id("me.saket.android.library.publishing")
  id("me.saket.kotlin.multiplatform")
}
apply(plugin = "app.cash.paparazzi")

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        implementation(compose("org.jetbrains.compose.ui:ui-util"))
        api(compose.foundation)
        api(libs.androidx.annotation)
      }
    }

    commonTest {
      dependencies {
        implementation(kotlin("test"))
      }
    }

    androidUnitTest {
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
