plugins {
    kotlin("jvm")
}

group = "eu.transittrack"

repositories {
    mavenCentral()
}

val assertkVersion = project.property("assertkVersion") as String

dependencies {
    // Point/Polyline appear in the public extension API, so expose them.
    api(project(":geo"))
    testImplementation(kotlin("test"))
    testImplementation("com.willowtreeapps.assertk:assertk:$assertkVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
