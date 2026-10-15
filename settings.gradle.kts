pluginManagement {
    val kotlinVersion = providers.gradleProperty("kotlinVersion").get()
    val springBootVersion = providers.gradleProperty("springBootVersion").get()
    val springDependencyManagementVersion = providers.gradleProperty("springDependencyManagementVersion").get()
    val spotlessVersion = providers.gradleProperty("spotlessVersion").get()
    val protobufPluginVersion = providers.gradleProperty("protobufPluginVersion").get()
    val kotlinxBenchmarkVersion = providers.gradleProperty("kotlinxBenchmarkVersion").get()

    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        kotlin("plugin.jpa") version kotlinVersion
        kotlin("kapt") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version springDependencyManagementVersion
        id("com.diffplug.spotless") version spotlessVersion
        id("com.google.protobuf") version protobufPluginVersion
        id("org.jetbrains.kotlinx.benchmark") version kotlinxBenchmarkVersion
    }
}

rootProject.name = "lens"
include(":extension-api")
include(":examples:example-extension")
