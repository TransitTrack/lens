import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.spring") version "2.4.20"
    kotlin("plugin.jpa") version "2.4.20"
    kotlin("kapt") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.2"
}

group = "eu.transittrack"
version = "0.0.1-SNAPSHOT"
description = "transittrack"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.springframework.boot:spring-boot-starter-kotlinx-serialization-json")
    modules {
        // Explicitly exclude jackson if you want to completely move away
        module("org.springframework.boot:spring-boot-starter-json") {
            replacedBy("org.springframework.boot:spring-boot-starter-kotlinx-serialization-json", "Use Kotlinx Serialization instead")
        }
    }

    implementation("com.graphql-java:graphql-java-extended-scalars:24.0")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.5.0")

    implementation("org.mobilitydata.gtfs-validator:gtfs-validator-main:8.0.1")
    implementation("org.mobilitydata.gtfs-validator:gtfs-validator-model:8.0.1")
    implementation("org.mobilitydata.gtfs-validator:gtfs-validator-core:8.0.1")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.google.flogger:flogger-slf4j-backend:0.8")
    implementation("com.google.guava:guava:33.6.0-jre")
    implementation("io.github.classgraph:classgraph:4.8.194")
    implementation("org.mobilitydata:gtfs-realtime-bindings:0.2.0")

    // developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    // runtimeOnly("com.julien-dubois.bootui:bootui-spring-boot-starter:1.17.0")

    kapt("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-cache-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-graphql-test")
    testImplementation("org.springframework.boot:spring-boot-starter-liquibase-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("com.willowtreeapps.assertk:assertk:0.28.1")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(kotlin("test"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            //            "-Xannotation-default-target=param-property"
        )
    }
}

spotless {
    // Apply styling to all Java files
    java {
        target("src/**/*.java")

        // 1. Enforce No Wildcards
        importOrder("java", "javax", "jakarta", "", "\\#")
        removeUnusedImports()

        // 2. Format with standard eclipse or google format if desired
        googleJavaFormat()
    }

    // Apply styling to all Kotlin files
    kotlin {
        target("src/**/*.kt")

        // Uses ktlint rules under the hood which honors your .editorconfig layout
        ktlint()

        // Optional additions:
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts") // Target your build scripts
        ktlint()
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // The AVL suite added several @SpringBootTest classes with distinct property combinations,
    // each caching its own Spring context (Testcontainers Postgres included); the default worker
    // heap is too small to hold them all alive when the whole suite runs in one JVM.
    maxHeapSize = "2g"
}

// Automatically apply styling whenever you run a Gradle build
tasks.withType<KotlinCompile>().configureEach {
    dependsOn("spotlessApply")
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn("spotlessApply")
}
