@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
  versionCatalogs {
    create("libs") {
      from(files("../libs.versions.toml"))
    }
  }
}

rootProject.name="build-logic"
include(":convention")
