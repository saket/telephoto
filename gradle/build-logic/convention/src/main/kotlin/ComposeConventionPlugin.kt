import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import com.android.build.gradle.BaseExtension as BaseAndroidExtension

@Suppress("UnstableApiUsage")
class ComposeConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    extensions.configure<BaseAndroidExtension> {
      buildFeatures.apply {
        compose = true
      }
      composeOptions {
        kotlinCompilerExtensionVersion = libs.findVersion("androidx.compose.compiler").get().toString()
      }
    }

    dependencies {
      add("implementation", libs.findLibrary("compose.runtime").get())
      add("lintChecks", libs.findLibrary("composeLintChecks").get())
    }
  }
}
