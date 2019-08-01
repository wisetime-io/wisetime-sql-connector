plugins {
    application
    checkstyle
    distribution
    idea
    jacoco
    java
    id("com.google.cloud.tools.jib") version "1.1.2"
    id("io.freefair.lombok") version "3.8.1"
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
    implementation("io.wisetime:wisetime-connector:2.2.2")
    implementation("org.apache.commons:commons-configuration2:2.4")
    implementation("com.google.guava:guava:27.1-jre")

    implementation("com.google.inject:guice:4.2.2:no_aop") {
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation("com.google.guava:guava:27.1-jre")

    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("ch.qos.logback:logback-core:1.2.3")
    implementation("org.slf4j:slf4j-api:1.7.26")

    implementation("org.codejargon:fluentjdbc:1.8.3")
    implementation("com.zaxxer:HikariCP:3.3.1")
    // Add more databases as we need to support them
    implementation("com.microsoft.sqlserver:mssql-jdbc:7.2.2.jre8")

    compileOnly("org.projectlombok:lombok:1.18.8")
    annotationProcessor("org.projectlombok:lombok:1.18.8")

    val junitVersion = "5.5.0"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    testImplementation("com.github.javafaker:javafaker:0.18")
    testImplementation("org.mockito:mockito-core:2.27.0")
    testImplementation("org.assertj:assertj-core:3.12.2")
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

checkstyle {
    toolVersion = "8.21"
    configProperties["checkstyleConfigDir"] = file("$rootDir/gradle")
    configFile = file("$rootDir/gradle/checkstyle.xml")
    isIgnoreFailures = false
    isShowViolations = true
}
