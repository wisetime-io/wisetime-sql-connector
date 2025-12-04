import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import io.wisetime.version.model.LegebuildConst
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  application
  checkstyle
  idea
  jacoco
  java
  `maven-publish`
  id("com.google.cloud.tools.jib") version "3.4.4"
  id("io.freefair.lombok") version "9.1.0"
  id("io.wisetime.versionChecker")
  id("fr.brouillard.oss.gradle.jgitver").version("0.9.1")
  id("com.github.ben-manes.versions").version("0.51.0")
}

repositories {
  mavenCentral()

  maven {
    // Our published releases repo
    setUrl("https://s3.eu-central-1.amazonaws.com/artifacts.wisetime.com/mvn2/releases")
    content {
      includeGroup("io.wisetime")
    }
  }
}

dependencies {
  implementation("io.wisetime:wisetime-connector:5.1.91")
  implementation("org.apache.httpcomponents:httpcore:4.4.16")
  implementation("commons-codec:commons-codec:1.17.2")
  implementation("io.vavr:vavr:0.10.5")
  implementation("org.apache.commons:commons-configuration2:2.12.0") {
    exclude("commons-logging")
  }
  implementation("com.google.guava:guava:${LegebuildConst.GUAVA_VERSION}")
  implementation("com.google.code.gson:gson:${LegebuildConst.GSON_GOOGLE}")

  implementation("ch.qos.logback:logback-classic:1.5.17")
  implementation("ch.qos.logback:logback-core:1.5.17")
  implementation("org.slf4j:slf4j-api:${LegebuildConst.SLF4J}")

  implementation("org.codejargon:fluentjdbc:1.8.6")
  implementation("com.zaxxer:HikariCP:7.0.2")
  // Add more databases as we need to support them
  implementation("com.microsoft.sqlserver:mssql-jdbc:7.4.1.jre8")
  implementation("org.antlr:antlr4-runtime:4.13.2")  // For MS SQL Server useFmtOnly feature
  implementation("mysql:mysql-connector-java:8.0.33") {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }
  implementation("org.postgresql:postgresql:42.7.5")

  implementation("org.yaml:snakeyaml:1.33")

  val junitVersion = "6.0.0"
  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

  testImplementation("io.wisetime:wisetime-test-support:2.9.24")
  testImplementation("org.flywaydb:flyway-core:7.15.0")
  testImplementation("com.github.javafaker:javafaker:1.0.2")
  testImplementation("org.mockito:mockito-core:3.12.4")
  testImplementation("org.assertj:assertj-core:3.27.2")
  testImplementation("org.junit.platform:junit-platform-launcher:1.13.4")
}

configurations.all {
  resolutionStrategy {
    eachDependency {
      if (requested.group == "com.fasterxml.jackson.core") {
        useVersion(LegebuildConst.JACKSON_FASTER)
        because("use consistent version for all transitive dependencies")
      }
      if (requested.name == "commons-lang3") {
        useVersion("3.18.0")
        because("use consistent version for all transitive dependencies")
      }
    }
    force("org.slf4j:jcl-over-slf4j:${LegebuildConst.SLF4J}")
    force("joda-time:joda-time:${LegebuildConst.JODA_TIME}")
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

application {
  mainClass.set("io.wisetime.connector.sql.ConnectorLauncher")
  applicationDefaultJvmArgs = listOf(
    "-server",
    "-Xms128m",
    "-Xmx256m",
    "-XX:+UseConcMarkSweepGC",
    "-XX:MaxMetaspaceSize=256m"
  )
}

apply(from = "$rootDir/gradle/jacoco.gradle")

jib {
  val targetArch: String? by project
  if (targetArch == "arm64v8") {
    from {
      image = "arm64v8/openjdk:11.0.8-jdk-buster"
    }
    to {
      project.afterEvaluate { // <-- so we evaluate version after it has been set
        image = "wisetime/wisetime-sql-connector-arm64v8:${project.version}"
      }
    }
  } else {
    from {
      image = "${project.properties["com.wisetime.jib.baseimage"]}"
    }
    to {
      project.afterEvaluate { // <-- so we evaluate version after it has been set
        image = "europe-west3-docker.pkg.dev/legebuild/connectors/wisetime-sql-connector:${project.version}"
      }
    }
  }
}

tasks.withType(com.google.cloud.tools.jib.gradle.JibTask::class.java) {
  dependsOn(tasks.compileJava)
}

jgitver {
  autoIncrementPatch(false)
  strategy(fr.brouillard.oss.jgitver.Strategies.PATTERN)
  versionPattern("\${meta.CURRENT_VERSION_MAJOR}.\${meta.CURRENT_VERSION_MINOR}.\${meta.COMMIT_DISTANCE}")
  regexVersionTag("v(\\d+\\.\\d+(\\.0)?)")
}

apply(from = "$rootDir/gradle/checkstyle.gradle")

tasks {
  check {
    dependsOn(jacocoTestCoverageVerification)
  }

  clean {
    setDelete(setOf("build", "out"))
  }

  dependencyUpdates {
    checkForGradleUpdate = false
    revision = "release"
    outputFormatter = "json"
    reportfileName = "report"
    outputDir = "$projectDir/build/dependencyUpdates"
  }

  withType<DependencyUpdatesTask> {
    rejectVersionIf {
      listOf("alpha", "beta", "b", "rc", "cr", "m", "preview", "snapshot", "ea")
        .any { skipItem -> candidate.version.lowercase().contains(skipItem) }
    }
  }

  test {
    testLogging {
      // skip logging PASSED
      setEvents(listOf(TestLogEvent.SKIPPED, TestLogEvent.FAILED))
      exceptionFormat = TestExceptionFormat.FULL
    }

    useJUnitPlatform {
      excludeTags = setOf("disabled", "integration", "integration-hq-stage")
    }

    finalizedBy(jacocoTestReport)
  }
}

publishing {
  repositories {
    maven {
      url = uri("s3://artifacts.wisetime.com/mvn2/releases")
      authentication {
        register<AwsImAuthentication>("awsIm")
      }
    }
  }
  publications {
    register<MavenPublication>("mavenJava") {
      groupId = "io.wisetime"
      artifactId = "wisetime-sql-connector"
      // version is set via plugin
      from(components["java"])
    }
  }
}

tasks.register<DefaultTask>("printVersionStr") {
  doLast {
    println("${project.version}")
  }
}
