import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.internal.impldep.org.junit.experimental.categories.Categories.CategoryFilter.exclude
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.exclude
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerArgumentsProducer.ArgumentType.Companion.all
import wtf.emulator.EwExtension
import java.time.Duration
import com.android.build.api.dsl.LibraryExtension as AndroidLibraryExtension
import com.android.build.gradle.BaseExtension as BaseAndroidExtension

class AndroidTestConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    plugins.run {
      apply("com.dropbox.dropshots")
      apply("wtf.emulator.gradle")
    }

    extensions.configure<AndroidLibraryExtension> {
      configureAndroid(this)
    }
    extensions.configure<BaseAndroidExtension> {
      defaultConfig {
        // targetSdk version has no effect for libraries. This is only used for
        // the test APK. Workarounds https://issuetracker.google.com/issues/283219177.
        targetSdk = libs.findVersion("compileSdk").get().toString().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      }
      testOptions.animationsDisabled = false
    }

    dependencies {
      add("androidTestImplementation", libs.findLibrary("androidx.test.ktx").get())
      add("androidTestImplementation", libs.findLibrary("androidx.test.rules").get())
      add("androidTestImplementation", libs.findLibrary("androidx.test.junit").get())
      add("androidTestImplementation", libs.findLibrary("compose.ui.test.junit").get())
      add("androidTestImplementation", libs.findLibrary("assertk").get())
      add("androidTestImplementation", libs.findLibrary("testParamInjector").get())
      add("androidTestImplementation", libs.findLibrary("compose.ui.test.activityManifest").get())
      add("androidTestImplementation", libs.findLibrary("leakcanary.test").get())
      add("debugImplementation", libs.findLibrary("leakcanary.core").get(), configureClosure {
        // Workaround for https://github.com/square/leakcanary/pull/2624.
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
      })
    }
    configurations.configureEach {
      // What the hell google https://stackoverflow.com/q/56639529.
      exclude(group = "com.google.guava", module = "listenablefuture")
    }

    emulatorwtf {
      version.set(libs.findVersion("emulatorWtfCli").get().toString())
      devices.set(
        listOf(
          mapOf("model" to "Pixel7Atd", "version" to 31)
        )
      )
      @Suppress("SdCardPath")
      directoriesToPull.set(listOf("/sdcard/Download/"))
      numUniformShards.set(3)
      numFlakyTestAttempts.set(2) // 3 runs in total.
      fileCacheTtl.set(Duration.ofDays(30))
      timeout.set(Duration.ofMinutes(15)) // Note to self: this is per shard and not per test.
      printOutput.set(true) // Print report URL even for successful test runs.
    }
  }
}

private fun Project.emulatorwtf(configure: Action<EwExtension>) =
  extensions.configure("emulatorwtf", configure)

private fun Any.configureClosure(action: ModuleDependency.() -> Unit): Closure<Any> {
  @Suppress("UNCHECKED_CAST")
  return closureOf(action) as Closure<Any>
}
