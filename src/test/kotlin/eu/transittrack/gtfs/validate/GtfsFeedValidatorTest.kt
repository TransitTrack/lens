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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import tools.jackson.databind.json.JsonMapper

/**
 * Plain JUnit (no Spring context, no database): exercises [GtfsFeedLoader]
 * against the checked-in GTFS fixture folders zipped on the fly.
 */
class GtfsFeedValidatorTest {

    private val validator = GtfsFeedLoader(JsonMapper.builder().build())
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
            dir.listDirectoryEntries().filter { it.isRegularFile() }.sortedBy { it.name }.forEach { f ->
                z.putNextEntry(ZipEntry(f.name).apply { time = 0L })
                z.write(f.readBytes())
                z.closeEntry()
            }
        }
        return zip to work
    }

    @Test
    fun `flags foreign key violations in the dangling-refs fixture`() {
        val (zip, work) = zipFixture("dangling-refs")

        val report = validator.load(zip)

        val fk = report.issues.find { it.rule == "foreign_key_violation" }
        assertNotNull(fk, "expected a foreign_key_violation issue, got ${report.issues.map { it.rule }}")
        assertEquals(Severity.ERROR, fk.severity)
        assertTrue(fk.count >= 1)
        assertTrue(report.errorCount >= 1)
    }

    @Test
    fun `minimal-valid fixture produces no errors`() {
        val (zip, work) = zipFixture("minimal-valid")

        val report = validator.load(zip)

        assertEquals(0L, report.errorCount, "unexpected errors: ${report.issues.filter { it.severity == Severity.ERROR }}")
    }

    @Test
    fun `report round-trips through toJson`() {
        val (zip, work) = zipFixture("minimal-valid")

        val json = validator.load(zip).toJson()

        assertTrue(json.startsWith("{"))
        assertTrue(json.contains("\"issues\""))
    }
}
