import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    plugins.run {
      apply("com.android.library")
    }

    extensions.configure<LibraryExtension> {
      defaultConfig {
        resourcePrefix = "_telephoto"
      }
      configureAndroid(this)
    }
  }
}
