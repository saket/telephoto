@file:Suppress("UnstableApiUsage")

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("org.jetbrains.compose")
  id("com.android.library")
}

kotlin {
  android {
    publishLibraryVariants("release")
    compilations.configureEach {
      kotlinOptions {
        jvmTarget = "11"
      }
    }
  }

  jvm("desktop") {
    compilations.configureEach {
      kotlinOptions {
        jvmTarget = "11"
      }
    }
  }

  targets.configureEach {
    compilations.configureEach {
      compilerOptions.configure {
        freeCompilerArgs.addAll(
          "-Xjvm-default=all", // TODO IDE complains this isn't set (transformableState.kt). is it working?
        )
      }
    }
  }

  sourceSets {
    named("commonMain") {
      dependencies {
        implementation(compose.runtime)
      }
    }

    named("commonTest") {
      dependencies {
        implementation(libs.assertk)
      }
    }
  }
}

compose {
  val compilerDependencyDeclaration = libs.androidx.compose.compiler.get().run { "$module:$version" }
  kotlinCompilerPlugin.set(compilerDependencyDeclaration)
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
    resourcePrefix = "_telephoto"
  }
  lint.abortOnError = true
}
