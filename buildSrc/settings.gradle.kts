@file:Suppress("UnstableApiUsage")

rootProject.name="telephoto"

dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
}
