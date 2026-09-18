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
import assertk.assertions.isNotNull

/** Plain JUnit: exercises [GtfsFeedLoader]'s [GtfsValidator] extension point (findings merged into
 * the report, independent of the canonical MobilityData validator's own notices). */
class GtfsFeedLoaderExtensionTest {
    private val workDirs = mutableListOf<Path>()

    @AfterTest
    fun cleanup() {
        workDirs.forEach { it.toFile().deleteRecursively() }
    }

    private fun zipFixture(fixture: String): Path {
        val work = Files.createTempDirectory("gfv-ext-$fixture-").also { workDirs.add(it) }
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
        return zip
    }

    @Test
    fun `merges a custom validator's findings into the report`() {
        val zip = zipFixture("minimal-valid")
        var seenInput: GtfsValidationInput? = null
        val fake =
            GtfsValidator { input ->
                seenInput = input
                listOf(
                    GtfsValidationFinding(
                        severity = GtfsValidationFinding.Severity.ERROR,
                        code = "custom_rule_violated",
                        message = "found it",
                    ),
                )
            }
        val loader = GtfsFeedLoader(listOf(fake))

        val report = loader.load(zip, "acme")

        val input = seenInput
        assertThat(input).isNotNull()
        assertThat(input!!.feedCode).isEqualTo("acme")
        assertThat(input.gtfsZipPath).isEqualTo(zip)

        val custom = report.issues.find { it.rule == "custom_rule_violated" }
        assertThat(custom).isNotNull()
        assertThat(custom!!.severity).isEqualTo(Severity.ERROR)
        assertThat(custom.sample).contains("found it")
        assertThat(report.errorCount).isEqualTo(1L)
    }

    @Test
    fun `no extension validators leaves the report unchanged`() {
        val zip = zipFixture("minimal-valid")

        val report = GtfsFeedLoader().load(zip, "acme")

        assertThat(report.errorCount).isEqualTo(0L)
    }
}
