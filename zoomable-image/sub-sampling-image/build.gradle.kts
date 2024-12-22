plugins {
  id("me.saket.android.library")
  id("me.saket.library.publishing")
  id("me.saket.kotlin.multiplatform")
  id("me.saket.android.test")
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.paparazzi)
}

android {
  namespace = "me.saket.telephoto.subsamplingimage"
  buildFeatures {
    buildConfig = true
  }
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(projects.zoomable)
      api(libs.okio.core)
      api(compose.foundation)
      implementation(compose.uiUtil)
      implementation(libs.kotlinx.immutableCollections)
    }

    androidMain {
      dependencies {
        implementation(libs.androidx.ktx.core)
        implementation(libs.androidx.exif)
        implementation(libs.androidx.tracing)
      }
    }

    androidUnitTest {
      dependencies {
        implementation(libs.junit)
        implementation(libs.assertk)
        implementation(libs.testParamInjector)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.turbine)
        implementation(libs.robolectric)
      }
    }

    androidInstrumentedTest {
      dependencies {
        implementation(projects.testUtil)
      }
    }
  }
}
