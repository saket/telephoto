import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
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
      testOptions.animationsDisabled = true
    }

    emulatorwtf {
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

internal fun Project.emulatorwtf(configure: Action<EwExtension>) =
  extensions.configure("emulatorwtf", configure)
