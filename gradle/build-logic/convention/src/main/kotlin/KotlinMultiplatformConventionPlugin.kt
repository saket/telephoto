import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.gradle.api.plugins.ExtensionAware as ExtensionAwarePlugin
import org.jetbrains.compose.ComposePlugin as JetbrainsComposePlugin

class KotlinMultiplatformConventionPlugin : Plugin<Project> {
  @OptIn(ExperimentalWasmDsl::class)
  override fun apply(target: Project) = with(target) {
    plugins.run {
      apply("org.jetbrains.kotlin.multiplatform")
      apply("org.jetbrains.kotlin.plugin.compose")
      apply("org.jetbrains.compose")
    }

    extensions.configure<KotlinMultiplatformExtension> {
      applyDefaultHierarchyTemplate()
      jvm("desktop")
      iosArm64()
      iosX64()
      iosSimulatorArm64()
      wasmJs()
      if (pluginManager.hasPlugin("com.android.library")) {
        androidTarget {
          publishLibraryVariants("release")
          compilerOptions {
            // https://developer.android.com/kotlin/parcelize#setup_parcelize_for_kotlin_multiplatform
            freeCompilerArgs.addAll("-P", "plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=me.saket.telephoto.zoomable.internal.AndroidParcelize")
          }
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
