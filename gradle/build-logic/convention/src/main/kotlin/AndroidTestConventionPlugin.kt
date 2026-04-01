import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.exclude
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
        targetSdk = versionCatalog.findVersion("compileSdk").get().toString().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      }
      testOptions.animationsDisabled = false
    }

    dependencies {
      add("androidTestImplementation", versionCatalog.findLibrary("androidx.test.ktx").get())
      add("androidTestImplementation", versionCatalog.findLibrary("androidx.test.rules").get())
      add("androidTestImplementation", versionCatalog.findLibrary("androidx.test.junit").get())
      add("androidTestImplementation", versionCatalog.findLibrary("compose.ui.test.junit").get())
      add("androidTestImplementation", versionCatalog.findLibrary("assertk").get())
      add("androidTestImplementation", versionCatalog.findLibrary("testParamInjector").get())
      add("androidTestImplementation", versionCatalog.findLibrary("compose.ui.test.activityManifest").get())
      add("androidTestImplementation", versionCatalog.findLibrary("leakcanary.test").get())
      add("debugImplementation", versionCatalog.findLibrary("leakcanary.core").get(), configureClosure {
        // Workaround for https://github.com/square/leakcanary/pull/2624.
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
      })
    }
    configurations.configureEach {
      // What the hell google https://stackoverflow.com/q/56639529.
      exclude(group = "com.google.guava", module = "listenablefuture")
    }

    emulatorwtf {
      val requiresPixelCopy = project.path == ":zoomable-peek-overlay"  // PixelCopy is unsupported on ATD devices.
      val requiresHwAcceleration = project.path == ":zoomable-image:interaction-tests"  // Software GPU results in janky animations.
      val requiresNonAtdDevice = requiresPixelCopy || requiresHwAcceleration

      version.set(versionCatalog.findVersion("emulatorWtfCli").get().toString())
      devices.set(
        listOf(
          mapOf(
            "model" to if (requiresNonAtdDevice) "Pixel7" else "Pixel7Atd",
            "gpu" to if (requiresHwAcceleration) "auto" else "software",
            "version" to 34,
          )
        )
      )
      @Suppress("SdCardPath")
      directoriesToPull.set(listOf("/sdcard/Download/"))
      numShards.set(5)
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
