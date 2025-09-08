plugins {
  id("me.saket.android.library")
  id("me.saket.library.publishing")
  id("me.saket.kotlin.multiplatform")
  id("me.saket.android.test")
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.paparazzi)
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(compose.foundation)
        api(projects.annotations)
      }
    }

    commonTest {
      dependencies {
        implementation(kotlin("test"))
      }
    }

    androidInstrumentedTest {
      dependencies {
        implementation(projects.testUtil)
        implementation(libs.androidx.test.uiautomator)
        implementation(libs.espresso.device)
        implementation(libs.kotlinx.immutableCollections)
      }
    }
  }
}

android {
  namespace = "me.saket.telephoto.zoomable"
}
