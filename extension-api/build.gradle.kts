plugins {
    kotlin("jvm")
    `java-library`
}

group = "eu.transittrack"

repositories {
    mavenCentral()
    maven { url = uri("https://repo.osgeo.org/repository/release/") }
}

val assertkVersion = project.property("assertkVersion") as String

dependencies {
    api(platform("org.geotools:gt-bom:34.0"))
    api("org.geotools:gt-main")
    api("org.geotools:gt-referencing")

    testImplementation(kotlin("test"))
    testImplementation("com.willowtreeapps.assertk:assertk:$assertkVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
