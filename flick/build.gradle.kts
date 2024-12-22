plugins {
  id("me.saket.android.library")
  id("me.saket.library.publishing")
  id("me.saket.kotlin.multiplatform")
  alias(libs.plugins.paparazzi)
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(compose.foundation)
        implementation(compose.uiUtil)
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
