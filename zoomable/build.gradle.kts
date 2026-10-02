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

    val desktopTest by getting {
      dependencies {
        implementation(compose.desktop.uiTestJUnit4)
        implementation(compose.desktop.currentOs)
        implementation(libs.kotlinx.coroutines.test)
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
