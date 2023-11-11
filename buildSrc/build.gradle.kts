plugins {
  `kotlin-dsl`
}

repositories {
  google()
  mavenCentral()
  gradlePluginPortal()
}

dependencies  {
  // https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

  implementation(libs.plugin.agp)
  implementation(libs.plugin.kotlin)
  implementation(libs.plugin.jetbrains.compose)
  implementation(libs.plugin.dokka)
  implementation(libs.plugin.mavenPublish)
  implementation(libs.plugin.dropshots)
  implementation(libs.plugin.emulatorWtf)
  implementation(libs.plugin.metalava)
  implementation(libs.plugin.poko)
}
