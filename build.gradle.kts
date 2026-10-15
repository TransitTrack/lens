import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
    id("com.google.protobuf")
    id("org.jetbrains.kotlinx.benchmark")
}

group = "eu.transittrack"
version = "0.0.1-SNAPSHOT"
description = "transitlens"

repositories {
    mavenCentral()
}

// A dedicated source set (rather than piggybacking on `main` or `test`) for load benchmarks of
// the ingest/match/predict pipeline: `associateWith` gives it access to both `main`'s internal
// classes and `test`'s support fixtures (Testcontainers Postgres wiring, FixtureDownloader-style
// helpers) without duplicating them, while keeping `./gradlew benchmark` a separate, opt-in task
// from the regular `test` build - these seed real Postgres data and run for minutes, not seconds.
// Declared before `dependencies {}` so the `benchmarkImplementation` configuration it creates
// exists by the time that block references it.
sourceSets {
    create("benchmark") {
        kotlin.srcDir("src/benchmark/kotlin")
        resources.srcDir("src/benchmark/resources")
    }
}

val graphqlJavaExtendedScalarsVersion = project.property("graphqlJavaExtendedScalarsVersion") as String
val okhttpVersion = project.property("okhttpVersion") as String
val gtfsValidatorVersion = project.property("gtfsValidatorVersion") as String
val gsonVersion = project.property("gsonVersion") as String
val floggerVersion = project.property("floggerVersion") as String
val guavaVersion = project.property("guavaVersion") as String
val classgraphVersion = project.property("classgraphVersion") as String
val bootuiVersion = project.property("bootuiVersion") as String
val mockitoKotlinVersion = project.property("mockitoKotlinVersion") as String
val assertkVersion = project.property("assertkVersion") as String
val protobufVersion = project.property("protobufVersion") as String
val shedlockVersion = project.property("shedlockVersion") as String
val kotlinxBenchmarkVersion = project.property("kotlinxBenchmarkVersion") as String

dependencies {
    implementation(project(":extension-api"))

    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.springframework.boot:spring-boot-starter-kotlinx-serialization-json")
    modules {
        // Explicitly exclude jackson if you want to completely move away
        module("org.springframework.boot:spring-boot-starter-json") {
            replacedBy("org.springframework.boot:spring-boot-starter-kotlinx-serialization-json", "Use Kotlinx Serialization instead")
        }
    }

    implementation("com.graphql-java:graphql-java-extended-scalars:$graphqlJavaExtendedScalarsVersion")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")

    implementation("org.mobilitydata.gtfs-validator:gtfs-validator-main:$gtfsValidatorVersion")
    implementation("org.mobilitydata.gtfs-validator:gtfs-validator-model:$gtfsValidatorVersion")
    implementation("org.mobilitydata.gtfs-validator:gtfs-validator-core:$gtfsValidatorVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")
    implementation("com.google.flogger:flogger-slf4j-backend:$floggerVersion")
    implementation("com.google.guava:guava:$guavaVersion")
    implementation("io.github.classgraph:classgraph:$classgraphVersion")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    // developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.julien-dubois.bootui:bootui-spring-boot-starter:$bootuiVersion")

    kapt("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-cache-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-graphql-test")
    testImplementation("org.springframework.boot:spring-boot-starter-liquibase-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okhttpVersion")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("com.willowtreeapps.assertk:assertk:$assertkVersion")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(kotlin("test"))

    protobuf(files("src/proto"))
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")

    implementation("net.javacrumbs.shedlock:shedlock-spring:$shedlockVersion")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:$shedlockVersion")

    testImplementation("net.javacrumbs.shedlock:shedlock-core:$shedlockVersion")

    "benchmarkImplementation"("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:$kotlinxBenchmarkVersion")
}

kotlin {
    target {
        val mainCompilation = compilations.getByName("main")
        val testCompilation = compilations.getByName("test")
        val benchmarkCompilation = compilations.getByName("benchmark")
        benchmarkCompilation.associateWith(mainCompilation)
        benchmarkCompilation.associateWith(testCompilation)
    }
}

benchmark {
    targets {
        register("benchmark")
    }
    configurations {
        named("main") {
            warmups = 1
            iterations = 3
        }
        // `./gradlew smokeBenchmark` - one class, one param value, one iteration: a fast sanity
        // check that the pipeline still runs end to end, not a real measurement.
        register("smoke") {
            include("AvlIngestBenchmark")
            param("vehicleCount", "400")
            warmups = 0
            iterations = 1
        }
    }
}

// Renders the most recent kotlinx-benchmark JSON report (already the raw JMH JSON format) as a
// single self-contained HTML page - no server, no upload, just `open` it in a browser. Uses
// Groovy's JsonSlurper (bundled with Gradle itself) rather than adding a JSON library to the
// buildscript classpath just for this.
tasks.register("benchmarkReport") {
    group = "benchmark"
    description = "Renders the most recent benchmark JSON report as a self-contained HTML page."
    doLast {
        val reportsDir = layout.buildDirectory
            .dir("reports/benchmarks")
            .get()
            .asFile
        val jsonFiles = reportsDir.walkTopDown().filter { it.isFile && it.name == "benchmark.json" }.toList()
        check(jsonFiles.isNotEmpty()) {
            "No benchmark.json found under $reportsDir - run ./gradlew benchmark or smokeBenchmark first."
        }
        val latest = jsonFiles.maxBy { it.lastModified() }

        @Suppress("UNCHECKED_CAST")
        val results = groovy.json.JsonSlurper().parse(latest) as List<Map<String, Any?>>

        val outFile = reportsDir.resolve("html/index.html")
        outFile.parentFile.mkdirs()
        outFile.writeText(renderBenchmarkReportHtml(results, latest.relativeTo(reportsDir).path))
        println("Wrote HTML benchmark report to ${outFile.toURI()}")
    }
}

@Suppress("UNCHECKED_CAST")
fun renderBenchmarkReportHtml(
    results: List<Map<String, Any?>>,
    sourcePath: String,
): String {
    data class Row(
        val benchmark: String,
        val shortName: String,
        val params: String,
        val mode: String,
        val score: Double,
        val error: Double?,
        val unit: String,
    )

    val rows =
        results.map { entry ->
            val benchmark = entry["benchmark"] as String
            val params = (entry["params"] as? Map<String, Any?>)?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: "-"
            val metric = entry["primaryMetric"] as Map<String, Any?>
            Row(
                benchmark = benchmark,
                shortName = benchmark.substringAfterLast('.'),
                params = params,
                mode = entry["mode"] as String,
                score = (metric["score"] as Number).toDouble(),
                error = (metric["scoreError"] as? Number)?.toDouble(),
                unit = metric["scoreUnit"] as String,
            )
        }

    // Bars are scaled within their own benchmark method group (e.g. AvlIngestBenchmark.ingest's
    // 400- and 4000-vehicle rows share a max), not across the whole report - a 4000-vehicle stress
    // run and a 400-vehicle realistic run aren't meant to be compared on the same scale.
    val maxScoreByBenchmark = rows.groupBy { it.benchmark }.mapValues { (_, v) -> v.maxOf { it.score } }

    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    val tableRows =
        rows.joinToString("\n") { r ->
            val max = maxScoreByBenchmark.getValue(r.benchmark)
            val pct = if (max > 0) (r.score / max * 100).coerceIn(0.0, 100.0) else 0.0
            val errorText = r.error?.let { " ± %.2f".format(it) } ?: ""
            val scoreText = "%.3f%s %s".format(r.score, errorText, r.unit)
            """
            <tr>
              <td>${escape(r.shortName)}</td>
              <td>${escape(r.params)}</td>
              <td>${escape(r.mode)}</td>
              <td class="score">
                <div class="bar-track"><div class="bar" style="width:${"%.1f".format(pct)}%"></div></div>
                <span class="score-text">${escape(scoreText)}</span>
              </td>
            </tr>
            """.trimIndent()
        }

    return """
        <!doctype html>
        <html lang="en">
        <head>
        <meta charset="utf-8">
        <title>TransitTrack Benchmark Report</title>
        <style>
          :root { color-scheme: light dark; }
          body { font: 14px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 2rem; }
          h1 { font-size: 1.4rem; }
          .source { color: #888; font-size: 0.85rem; margin-bottom: 1.5rem; }
          table { border-collapse: collapse; width: 100%; max-width: 900px; }
          th, td { text-align: left; padding: 0.5rem 0.75rem; border-bottom: 1px solid #8883; vertical-align: middle; }
          th { font-weight: 600; }
          td.score { min-width: 260px; }
          .bar-track { background: #8882; border-radius: 3px; height: 10px; width: 160px; display: inline-block; vertical-align: middle; }
          .bar { background: #4a9eff; height: 10px; border-radius: 3px; }
          .score-text { margin-left: 0.6rem; font-variant-numeric: tabular-nums; }
        </style>
        </head>
        <body>
        <h1>TransitTrack Benchmark Report</h1>
        <div class="source">Source: $sourcePath</div>
        <table>
          <thead><tr><th>Benchmark</th><th>Params</th><th>Mode</th><th>Score</th></tr></thead>
          <tbody>
        $tableRows
          </tbody>
        </table>
        </body>
        </html>
        """.trimIndent()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            //            "-Xannotation-default-target=param-property"
        )
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
}

spotless {
    // Apply styling to all Java files
    java {
        target("src/**/*.java")

        // 1. Enforce No Wildcards
        importOrder("java", "javax", "jakarta", "", "\\#")
        removeUnusedImports()

        // 2. Format with standard eclipse or google format if desired
        googleJavaFormat()
    }

    // Apply styling to all Kotlin files
    kotlin {
        target("src/**/*.kt")

        // Uses ktlint rules under the hood which honors your .editorconfig layout
        ktlint()

        // Optional additions:
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts") // Target your build scripts
        ktlint()
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
    // JMH (which kotlinx-benchmark drives under the hood) generates subclasses of @State classes,
    // which requires the class and its @Benchmark methods to be non-final.
    annotation("org.openjdk.jmh.annotations.State")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // The AVL suite added several @SpringBootTest classes with distinct property combinations,
    // each caching its own Spring context (Testcontainers Postgres included); the default worker
    // heap is too small to hold them all alive when the whole suite runs in one JVM.
    maxHeapSize = "2g"
}

// ExtensionLoadingTest only passes with the example-extension jar on loader.path (see the
// extensionLoadingTest task below), so it's excluded from the default suite, which shouldn't
// require that module to be built.
tasks.test {
    exclude("**/ExtensionLoadingTest.class")
}

evaluationDependsOn(":examples:example-extension")

tasks.register<Test>("extensionLoadingTest") {
    // `-Dloader.path` alone only does something when PropertiesLauncher.main() bootstraps the
    // JVM's classpath from it; a @SpringBootTest run inside this Gradle worker calls
    // SpringApplication.run() directly and never goes through that launcher, so the extension jar
    // has to be put on the test's own classpath for the AutoConfiguration.imports discovery this
    // test proves to actually see it. `loader.path` is still set, matching how the packaged app
    // is launched in production (see the Dockerfile/bootJar wiring above).
    val exampleExtensionJar = project(":examples:example-extension").tasks.named<Jar>("jar")
    dependsOn(exampleExtensionJar)
    useJUnitPlatform()
    include("**/ExtensionLoadingTest.class")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath + files(exampleExtensionJar.flatMap { it.archiveFile })
    doFirst {
        systemProperty(
            "loader.path",
            exampleExtensionJar
                .get()
                .archiveFile
                .get()
                .asFile.parentFile.absolutePath,
        )
    }
}

// PropertiesLauncher (instead of the default JarLauncher) reads `loader.path` at startup and adds
// every jar/directory listed there to the app classloader, so extension jars dropped into
// /extensions (see Dockerfile) are picked up without repackaging the fat jar.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    manifest {
        attributes("Main-Class" to "org.springframework.boot.loader.launch.PropertiesLauncher")
    }
}

// Automatically apply styling whenever you run a Gradle build
tasks.withType<KotlinCompile>().configureEach {
    dependsOn("spotlessApply")
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn("spotlessApply")
}
