plugins {
  `kotlin-dsl`
}

dependencies {
  compileOnly(libs.plugin.agp)
  compileOnly(libs.plugin.kotlin)
  compileOnly(libs.plugin.jetbrains.compose)
  compileOnly(libs.plugin.dokka)
  compileOnly(libs.plugin.mavenPublish)
  compileOnly(libs.plugin.dropshots)
  compileOnly(libs.plugin.emulatorWtf)
  compileOnly(libs.plugin.metalava)
  compileOnly(libs.plugin.poko)
}

gradlePlugin {
  plugins {
    register("kotlinMultiplatform") {
      id = "me.saket.kotlin.multiplatform"
      implementationClass = "KotlinMultiplatformConventionPlugin"
    }
    register("kotlinAndroid") {
      id = "me.saket.kotlin.android"
      implementationClass = "KotlinAndroidConventionPlugin"
    }
    register("androidApplication") {
      id = "me.saket.android.application"
      implementationClass = "AndroidApplicationConventionPlugin"
    }
    register("androidLibrary") {
      id = "me.saket.android.library"
      implementationClass = "AndroidLibraryConventionPlugin"
    }
    register("androidLibraryPublishing") {
      id = "me.saket.android.library.publishing"  // todo: rename to me.saket.library.publishing
      implementationClass = "AndroidLibraryPublishingConventionPlugin"
    }
    register("androidTest") {
      id = "me.saket.android.test"
      implementationClass = "AndroidTestConventionPlugin"
    }
    register("compose") {
      id = "me.saket.compose"
      implementationClass = "ComposeConventionPlugin"
    }
  }
}
