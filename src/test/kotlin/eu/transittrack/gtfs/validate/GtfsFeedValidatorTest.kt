package eu.transittrack.gtfs.validate

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue

/** Plain JUnit (no Spring context, no database): exercises [GtfsFeedLoader] against the checked-in GTFS fixture folders zipped on the fly. */
class GtfsFeedValidatorTest {
    private val validator = GtfsFeedLoader()
    private val workDirs = mutableListOf<Path>()

    @AfterTest
    fun cleanup() {
        workDirs.forEach { it.toFile().deleteRecursively() }
    }

    private fun zipFixture(fixture: String): Pair<Path, Path> {
        val work = Files.createTempDirectory("gfv-$fixture-").also { workDirs.add(it) }
        val zip = work.resolve("feed.zip")
        val dir = Path("src/test/resources/gtfs/$fixture")
        ZipOutputStream(Files.newOutputStream(zip)).use { z ->
            dir
                .listDirectoryEntries()
                .filter { it.isRegularFile() }
                .sortedBy { it.name }
                .forEach { f ->
                    z.putNextEntry(ZipEntry(f.name).apply { time = 0L })
                    z.write(f.readBytes())
                    z.closeEntry()
                }
        }
        return zip to work
    }

    @Test
    fun `flags referential errors in the dangling-refs fixture`() {
        val (zip, _) = zipFixture("dangling-refs")

        val report = validator.load(zip)

        val fk = report.issues.find { it.rule == "TRP_002" }
        assertThat(fk).isNotNull()
        assertThat(fk!!.severity).isEqualTo(Severity.ERROR)
        assertThat(fk.count).isGreaterThanOrEqualTo(1)
        assertThat(report.errorCount).isGreaterThanOrEqualTo(1L)
        assertThat(report.publishable).isFalse()
    }

    @Test
    fun `minimal-valid fixture produces no errors`() {
        val (zip, _) = zipFixture("minimal-valid")

        val report = validator.load(zip)

        assertThat(report.errorCount).isEqualTo(0L)
        assertThat(report.publishable).isTrue()
    }

    @Test
    fun `report round-trips through toJson`() {
        val (zip, _) = zipFixture("dangling-refs")
        val report = validator.load(zip)

        val json = report.toJson()

        assertThat(json).contains("\"format\":2")
        val decoded = LoadValidationReport.fromJson(json)
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.issues).isEqualTo(report.issues)
        assertThat(decoded.publishable).isEqualTo(report.publishable)
    }

    @Test
    fun `missing required file is PARTIAL with stop_times unavailable`() {
        val (zip, _) = zipFixture("missing-required-file")

        val report = validator.load(zip)

        assertThat(report.fatal).isNull()
        assertThat(report.unavailableFiles).isEqualTo(listOf("stop_times.txt"))
        assertThat(report.publishable).isFalse()
    }

    @Test
    fun `non-zip input is fatal`() {
        val work = Files.createTempDirectory("gfv-fatal-").also { workDirs.add(it) }
        val zip = work.resolve("feed.zip").also { Files.writeString(it, "not a zip") }

        val report = validator.load(zip)

        assertThat(report.fatal).isNotNull()
        assertThat(report.issues.single().severity).isEqualTo(Severity.ERROR)
        assertThat(report.errorCount).isEqualTo(1L)
        assertThat(report.publishable).isFalse()
    }

    @Test
    fun `old format json is not decoded`() {
        assertThat(LoadValidationReport.fromJson("""{"issues":[]}""")).isNull()
    }

    @Test
    fun `undecodable format 2 json yields null instead of throwing`() {
        assertThat(LoadValidationReport.fromJson("""{"format":2,"analyzer":{"version":"x"}}""")).isNull()
        assertThat(LoadValidationReport.fromJson("""[1,2]""")).isNull()
        assertThat(LoadValidationReport.fromJson("""{"format":"two"}""")).isNull()
    }
}
