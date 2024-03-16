import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureKotlin() {
  tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
      freeCompilerArgs.addAll(
        "-Xjvm-default=all",
        "-Xcontext-receivers",
        "-Xexpect-actual-classes", // https://youtrack.jetbrains.com/issue/KT-61573
      )
    }
  }

  val targetJdkVersion = "11"
  tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(targetJdkVersion))
    }
  }
  extensions.findByType(JavaPluginExtension::class.java)?.apply {
    sourceCompatibility = JavaVersion.toVersion(targetJdkVersion)
    targetCompatibility = JavaVersion.toVersion(targetJdkVersion)
  }
  extensions.findByType(CommonExtension::class.java)?.apply {
    compileOptions {
      sourceCompatibility = JavaVersion.toVersion(targetJdkVersion)
      targetCompatibility = JavaVersion.toVersion(targetJdkVersion)
    }
  }
  kotlinExtension.jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(targetJdkVersion))
  }
}
