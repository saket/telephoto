import org.gradle.api.Plugin
import org.gradle.api.Project

class KotlinAndroidConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    plugins.apply("org.jetbrains.kotlin.android")
    configureKotlin()
  }
}
