plugins {
  id("me.saket.android.library")
  id("me.saket.kotlin.android")
  id("me.saket.compose")
}

android {
  namespace = "me.saket.telephoto.testutil"
}

dependencies {
  implementation(libs.dropshots) {
    // Gradle fails to override this transitive dependency's version
    // with the one used by telephoto. Not sure why this is needed.
    exclude(group = "androidx.test")
  }
  implementation(libs.dropboxDiffer)
  implementation(libs.compose.ui.test.junit)
  implementation(libs.androidx.test.runner) // Needed for Screenshot.capture().
  implementation(libs.assertk)
}
