rootProject.name = "wisetime-sql-connector"

pluginManagement {

  plugins {
    id("io.wisetime.versionChecker") version "10.13.85"
  }

  resolutionStrategy.eachPlugin {
    if (requested.id.id == "com.google.protobuf") {
      useModule("com.google.protobuf:protobuf-gradle-plugin:0.8.9")
    }
  }

  repositories {
    gradlePluginPortal()
    mavenCentral()
    maven {
      url = uri("https://europe-west3-maven.pkg.dev/wise-pub/gradle")
      content {
        includeGroup("io.wisetime.versionChecker")
      }
    }
  }
}
