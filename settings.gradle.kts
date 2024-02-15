@file:Suppress("UnstableApiUsage")

pluginManagement {
  includeBuild("gradle/build-logic")
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven(url = "https://maven.emulator.wtf/releases/") {
      content { includeGroup("wtf.emulator") }
    }
  }
}

rootProject.name="telephoto"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":benchmark:runner")
include(":flick")
include(":zoomable")
include(":zoomable-image:core")
include(":zoomable-image:coil")
include(":zoomable-image:glide")
include(":zoomable-image:sub-sampling-image")
include(":sample")
include(":test-util")

