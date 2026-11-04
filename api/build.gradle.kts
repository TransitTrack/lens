plugins {
    kotlin("jvm")
}

group = "eu.transittrack"

repositories {
    mavenCentral()
}

val assertkVersion = project.property("assertkVersion") as String

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("com.willowtreeapps.assertk:assertk:$assertkVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
