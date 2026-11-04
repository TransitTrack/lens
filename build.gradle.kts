plugins {
    base
    kotlin("jvm") apply false
    kotlin("plugin.spring") apply false
    kotlin("kapt") apply false
    kotlin("plugin.serialization") apply false
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
    id("com.diffplug.spotless")
    id("com.google.protobuf") apply false
    id("org.jetbrains.kotlinx.benchmark") apply false
}

repositories {
    mavenCentral()
}

spotless {
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}
