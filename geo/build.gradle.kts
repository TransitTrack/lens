plugins {
    kotlin("jvm")
}

group = "eu.transittrack"

repositories {
    mavenCentral()
}

val assertkVersion = project.property("assertkVersion") as String
val kotlinxSerializationVersion = project.property("kotlinxSerializationVersion") as String

// Standalone geometry library (WGS-84 points, segments, polylines, extents): no Spring, no
// framework dependencies (only kotlinx-serialization-json for GeoJSON encoding), so it can be lifted into its own repository unchanged.
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    testImplementation(kotlin("test"))
    testImplementation("com.willowtreeapps.assertk:assertk:$assertkVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
