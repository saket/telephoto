import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

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
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
      compilerOptions {
        allWarningsAsErrors.set(true)
      }
    }
  }
}
