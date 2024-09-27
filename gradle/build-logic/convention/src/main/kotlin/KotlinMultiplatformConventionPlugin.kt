import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.gradle.api.plugins.ExtensionAware as ExtensionAwarePlugin
import org.jetbrains.compose.ComposePlugin as JetbrainsComposePlugin

class KotlinMultiplatformConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    plugins.run {
      apply("org.jetbrains.kotlin.multiplatform")
      apply("org.jetbrains.compose")
    }

    extensions.configure<ComposeExtension> {
      val compilerDependencyDeclaration = libs.findLibrary("androidx.compose.compiler").get().get().let {
        "${it.module}:${it.version}"
      }
      kotlinCompilerPlugin.set(compilerDependencyDeclaration)
    }

    extensions.configure<KotlinMultiplatformExtension> {
      applyDefaultHierarchyTemplate()
      jvm("desktop")
      iosArm64()
      iosX64()
      iosSimulatorArm64()
      if (pluginManager.hasPlugin("com.android.library")) {
        androidTarget {
          publishLibraryVariants("release")
        }
      }

      configureKotlin()

      sourceSets.run {
        commonMain {
          dependencies {
            implementation(compose.runtime)
          }
        }
        commonTest {
          dependencies {
            implementation(libs.findLibrary("assertk").get())
          }
        }
      }
    }
  }
}

private val KotlinMultiplatformExtension.compose: JetbrainsComposePlugin.Dependencies
  get() = (this as ExtensionAwarePlugin).extensions.getByName("compose") as JetbrainsComposePlugin.Dependencies
