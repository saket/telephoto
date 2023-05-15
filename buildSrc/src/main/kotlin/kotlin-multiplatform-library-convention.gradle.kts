@file:Suppress("UnstableApiUsage")

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("org.jetbrains.compose")
  id("com.android.library")
}

kotlin {
  android {
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

  // TODO what other targets?

  targets.configureEach {
    compilations.configureEach {
      compilerOptions.configure {
        freeCompilerArgs.addAll(
          "-Xcontext-receivers",
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
  }
}

compose {
  val compilerDependencyDeclaration = libs.androidx.compose.compiler.get()
    .run { "$module:$version" }
  kotlinCompilerPlugin.set(compilerDependencyDeclaration)
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
    resourcePrefix = "_telephoto"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  lint.abortOnError = true
}
