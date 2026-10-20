plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

group = "com.example.transittrackext"

repositories {
    mavenCentral()
    maven { url = uri("https://repo.osgeo.org/repository/release/") }
}

val springContextVersion = project.property("springContextVersion") as String

dependencies {
    implementation(project(":extension-api"))
    implementation("org.springframework:spring-context:$springContextVersion")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
