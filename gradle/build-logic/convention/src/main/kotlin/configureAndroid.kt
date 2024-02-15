import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project

internal fun Project.configureAndroid(commonExtension: CommonExtension <*, *, *, *, *, *>) {
  commonExtension.apply {
    compileSdk = libs.findVersion("compileSdk").get().toString().toInt()
    defaultConfig {
      minSdk = libs.findVersion("minSdk").get().toString().toInt()
      resourcePrefix = "_telephoto"
    }
    lint {
      abortOnError = true
    }

    // https://github.com/Kotlin/kotlinx.coroutines/blob/35d88f1ba5bda4d69aa717bf714b88f39a356010/README.md?plain=1#L149-L159
    packaging {
      resources.excludes += "DebugProbesKt.bin"
    }
  }
}
