rootProject.name = "wisetime-sql-connector"

pluginManagement {

  plugins {
    id("io.wisetime.versionChecker") version "10.12.68"
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
      setUrl("https://s3.eu-central-1.amazonaws.com/artifacts.wisetime.com/mvn2/plugins")
      content {
        includeGroup("io.wisetime.versionChecker")
      }
    }
  }
}
