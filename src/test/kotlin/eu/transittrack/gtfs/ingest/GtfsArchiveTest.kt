package eu.transittrack.gtfs.ingest

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull

import eu.transittrack.exception.ParseException

class GtfsArchiveTest {
    private val tmp = Files.createTempDirectory("arch")

    private fun zip(vararg entries: Pair<String, String>): java.nio.file.Path {
        val z = tmp.resolve("feed-${System.nanoTime()}.zip")
        ZipOutputStream(Files.newOutputStream(z)).use { out ->
            for ((name, body) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(body.toByteArray())
                out.closeEntry()
            }
        }
        return z
    }

    @Test
    fun `extracts flat feed`() {
        val dest = Files.createTempDirectory("d1")
        val names =
            GtfsArchive.extract(
                zip("agency.txt" to "agency_id\nA", "stops.txt" to "stop_id\nS"),
                dest,
            )
        assertThat(names.toSet()).isEqualTo(setOf("agency.txt", "stops.txt"))
        assertThat(GtfsArchive.openFile(dest, "agency.txt")).isNotNull()
    }

    @Test
    fun `flattens single wrapper directory`() {
        val dest = Files.createTempDirectory("d2")
        val names = GtfsArchive.extract(zip("gtfs/agency.txt" to "x", "gtfs/routes.txt" to "y"), dest)
        assertThat(names.toSet()).isEqualTo(setOf("agency.txt", "routes.txt"))
    }

    @Test
    fun `rejects zip slip`() {
        val dest = Files.createTempDirectory("d3")
        assertFailure {
            GtfsArchive.extract(zip("../evil.txt" to "x"), dest)
        }.isInstanceOf<ParseException>()
    }
}
