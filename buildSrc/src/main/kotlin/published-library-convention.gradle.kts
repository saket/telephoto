@file:Suppress("UnstableApiUsage")

plugins {
  id("com.vanniktech.maven.publish")
  id("org.jetbrains.dokka")
}

// Used on CI to publish snapshot versions.
tasks.register("throwIfVersionIsNotSnapshot") {
  val libraryVersion = properties["VERSION_NAME"] as String
  check(libraryVersion.endsWith("SNAPSHOT")) {
    "Project isn't using a snapshot version = $libraryVersion"
  }
}
