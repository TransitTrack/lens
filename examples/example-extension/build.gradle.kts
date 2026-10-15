plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

group = "com.example.transittrackext"

repositories {
    mavenCentral()
}

val springContextVersion = project.property("springContextVersion") as String

dependencies {
    implementation(project(":extension-api"))
    implementation("org.springframework:spring-context:$springContextVersion")
}
