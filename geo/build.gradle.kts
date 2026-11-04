plugins {
    kotlin("jvm")
}

group = "eu.transittrack"

repositories {
    mavenCentral()
}

val assertkVersion = project.property("assertkVersion") as String

// Standalone geometry library (WGS-84 points, segments, polylines, extents): no Spring, no
// framework dependencies, so it can be lifted into its own repository unchanged.
dependencies {
    testImplementation(kotlin("test"))
    testImplementation("com.willowtreeapps.assertk:assertk:$assertkVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
