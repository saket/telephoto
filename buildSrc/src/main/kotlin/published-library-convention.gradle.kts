@file:Suppress("UnstableApiUsage")

plugins {
  id("com.vanniktech.maven.publish")
  id("org.jetbrains.dokka")
  id("me.tylerbwong.gradle.metalava")
}

// Used on CI to prevent publishing of non-snapshot versions.
tasks.register("throwIfVersionIsNotSnapshot") {
  val libraryVersion = properties["VERSION_NAME"] as String
  check(libraryVersion.endsWith("SNAPSHOT")) {
    "Project isn't using a snapshot version = $libraryVersion"
  }
}

metalava {
  filename.set("api/api.txt")
  enforceCheck.set(true)
  sourcePaths.setFrom("src/main", "src/commonMain") // Exclude tests.
}
