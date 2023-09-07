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
  id("com.google.cloud.tools.jib") version "3.3.1"
  id("io.freefair.lombok") version "8.0.1"
  id("io.wisetime.versionChecker")
  id("fr.brouillard.oss.gradle.jgitver").version("0.9.1")
  id("com.github.ben-manes.versions").version("0.46.0")
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
  implementation("io.wisetime:wisetime-connector:5.1.6")
  implementation("org.apache.httpcomponents:httpcore:4.4.14")
  implementation("org.springframework.boot:spring-boot-starter-validation:2.5.4") {
    exclude("org.apache.logging.log4j", "log4j-api")
    exclude("org.slf4j", "jul-to-slf4j")
  }
  implementation("commons-codec:commons-codec:1.15")
  implementation("io.vavr:vavr:0.10.3")
  implementation("org.apache.commons:commons-configuration2:2.4") {
    exclude("commons-logging")
  }
  implementation("com.google.guava:guava:${LegebuildConst.GUAVA_VERSION}")
  implementation("com.google.code.gson:gson:${LegebuildConst.GSON_GOOGLE}")

  implementation("ch.qos.logback:logback-classic:1.4.5")
  implementation("ch.qos.logback:logback-core:1.4.5")
  implementation("org.slf4j:slf4j-api:${LegebuildConst.SLF4J}")

  implementation("org.codejargon:fluentjdbc:1.8.6")
  implementation("com.zaxxer:HikariCP:5.0.1")
  // Add more databases as we need to support them
  implementation("com.microsoft.sqlserver:mssql-jdbc:7.4.1.jre8")
  implementation("org.antlr:antlr4-runtime:4.8-1")  // For MS SQL Server useFmtOnly feature
  implementation("mysql:mysql-connector-java:8.0.33") {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }
  implementation("org.postgresql:postgresql:42.6.0")

  implementation("org.yaml:snakeyaml:1.24")

  val junitVersion = "5.9.3"
  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

  testImplementation("io.wisetime:wisetime-test-support:${LegebuildConst.WT_TEST_SUPPORT}")
  testImplementation("org.flywaydb:flyway-core:7.5.4")
  testImplementation("com.github.javafaker:javafaker:1.0.1")
  testImplementation("org.mockito:mockito-core:3.0.0")
  testImplementation("org.assertj:assertj-core:3.24.2")
}

configurations.all {
  resolutionStrategy {
    eachDependency {
      if (requested.group == "com.fasterxml.jackson.core") {
        useVersion(LegebuildConst.JACKSON_FASTER)
        because("use consistent version for all transitive dependencies")
      }
      if (requested.name == "commons-lang3") {
        useVersion("3.12.0")
        because("use consistent version for all transitive dependencies")
      }
    }
    force("org.slf4j:jcl-over-slf4j:${LegebuildConst.SLF4J}")
    force("joda-time:joda-time:${LegebuildConst.JODA_TIME}")
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
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
      image = "europe-west3-docker.pkg.dev/wise-pub/tools/jdk/jdk-17:17.0.7.slim"
    }
    to {
      project.afterEvaluate { // <-- so we evaluate version after it has been set
        image = "wisetime/wisetime-sql-connector:${project.version}"
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

  "dependencyUpdates"(DependencyUpdatesTask::class) {
    resolutionStrategy {
      componentSelection {
        all {
          val skipList = listOf("alpha", "beta", "rc", "cr", "m", "preview", "snapshot")
          for (skipItem in skipList) {
            if (candidate.group.contains("io.wisetime")
              && candidate.version.matches(Regex("\\d+(\\.\\d+)+-\\d+.*"))
            ) {
              reject("ignore prerelease versions based on commit distance")
            }
            if (candidate.version.toLowerCase().contains(skipItem)) {
              reject("skip version containing `$skipItem`")
            }
          }
        }
      }
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
