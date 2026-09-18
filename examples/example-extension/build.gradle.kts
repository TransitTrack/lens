plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.spring") version "2.4.20"
}

group = "com.example.transittrackext"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":extension-api"))
    implementation("org.springframework:spring-context:7.0.9")
}
