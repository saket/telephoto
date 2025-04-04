import me.tylerbwong.gradle.metalava.extension.MetalavaExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryPublishingConventionPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit = with(target) {
    plugins.run {
      apply("com.vanniktech.maven.publish")
      apply("org.jetbrains.dokka")
      apply("me.tylerbwong.gradle.metalava")
      apply("dev.drewhamilton.poko")
    }

    // Used on CI to prevent publishing of non-snapshot versions.
    tasks.register("throwIfVersionIsNotSnapshot") {
      doLast {
        val libraryVersion = properties["VERSION_NAME"] as String
        check(libraryVersion.endsWith("SNAPSHOT")) {
          "Project isn't using a snapshot version = $libraryVersion"
        }
      }
    }

    extensions.configure<MetalavaExtension> {
      filename.set("api/api.txt")
      enforceCheck.set(true)
      apiCompatAnnotations.set(
        listOf(
          "androidx.compose.runtime.Stable",
          "androidx.compose.runtime.Immutable",
          "androidx.compose.runtime.Composable",
          "dev.drewhamilton.poko.Poko",
        )
      )
    }
  }
}
