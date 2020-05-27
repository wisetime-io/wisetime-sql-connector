import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    checkstyle
    distribution
    idea
    jacoco
    java
    `maven-publish`
    id("com.google.cloud.tools.jib") version "1.4.0"
    id("io.freefair.lombok") version "3.8.4"
    id("io.wisetime.versionChecker").version("0.9.13")
    id("fr.brouillard.oss.gradle.jgitver").version("0.9.1")
    id("com.github.ben-manes.versions").version("0.21.0")
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
    implementation("io.wisetime:wisetime-connector:2.3.4")
    implementation("org.apache.commons:commons-configuration2:2.5")
    implementation("com.google.guava:guava:28.2-jre")

    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("ch.qos.logback:logback-core:1.2.3")
    implementation("org.slf4j:slf4j-api:1.7.30")

    implementation("org.codejargon:fluentjdbc:1.8.3")
    implementation("com.zaxxer:HikariCP:3.3.1")
    // Add more databases as we need to support them
    implementation("com.microsoft.sqlserver:mssql-jdbc:7.4.1.jre8")
    implementation("org.antlr:antlr4-runtime:4.8-1")  // For MS SQL Server useFmtOnly feature

    compileOnly("org.projectlombok:lombok:1.18.8")
    annotationProcessor("org.projectlombok:lombok:1.18.8")

    implementation("org.yaml:snakeyaml:1.24")

    val junitVersion = "5.5.1"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    testImplementation("io.wisetime:wisetime-test-support:2.4.0-0")
    testImplementation("org.flywaydb:flyway-core:5.2.4")
    testImplementation("com.github.javafaker:javafaker:1.0.1")
    testImplementation("org.mockito:mockito-core:3.0.0")
    testImplementation("org.assertj:assertj-core:3.13.2")
}

java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

application {
    mainClassName = "ConnectorLauncher"
    applicationDefaultJvmArgs = listOf(
            "-server",
            "-Xms128m",
            "-Xmx256m",
            "-XX:+UseConcMarkSweepGC",
            "-XX:MetaspaceSize=128m",
            "-XX:MaxMetaspaceSize=256m")
}

jacoco {
    toolVersion = "0.8.4"
}
apply(from = "$rootDir/gradle/jacoco.gradle")

jib {
    val targetArch: String? by project
    if (targetArch == "arm64v8") {
        println("Building image with architecture: arm64v8")
        from {
            image = "arm64v8/openjdk:8u201-jdk-alpine"
        }
        to {
            image = "wisetime/wisetime-sql-connector-arm64v8"
        }
    } else {
        println("Building image with (default) architecture: amd64")
        to {
            image = "wisetime/wisetime-sql-connector"
        }
    }
}

jgitver {
    autoIncrementPatch = false
}

checkstyle {
    toolVersion = "8.21"
    configProperties["checkstyleConfigDir"] = file("$rootDir/gradle")
    configFile = file("$rootDir/gradle/checkstyle.xml")
    isIgnoreFailures = false
    isShowViolations = true
}

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
                                && candidate.version.matches(Regex("\\d+(\\.\\d+)+-\\d+.*"))) {
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

    jacocoTestReport {
        reports.findByName("xml")?.isEnabled = true
        reports.findByName("csv")?.isEnabled = false

        // disable html creation if droneTest property was set
        reports.findByName("html")?.isEnabled = !project.hasProperty("droneTest")
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
