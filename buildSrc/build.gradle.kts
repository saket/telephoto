plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  google()
}

dependencies  {
  implementation("com.android.tools.build:gradle:7.4.2")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")
  implementation("com.dropbox.dropshots:dropshots-gradle-plugin:0.4.0")
  implementation("wtf.emulator:gradle-plugin:0.9.5")
}
