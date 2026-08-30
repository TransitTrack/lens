package eu.transittrack.gtfs.ingest

import eu.transittrack.gtfs.parse.GtfsParseException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class GtfsArchiveTest {

    private val tmp = Files.createTempDirectory("arch")

    private fun zip(vararg entries: Pair<String, String>): java.nio.file.Path {
        val z = tmp.resolve("feed-${System.nanoTime()}.zip")
        ZipOutputStream(Files.newOutputStream(z)).use { out ->
            for ((name, body) in entries) {
                out.putNextEntry(ZipEntry(name)); out.write(body.toByteArray()); out.closeEntry()
            }
        }
        return z
    }

    @Test fun `extracts flat feed`() {
        val dest = Files.createTempDirectory("d1")
        val names = GtfsArchive.extract(zip("agency.txt" to "agency_id\nA", "stops.txt" to "stop_id\nS"), dest)
        assertEquals(setOf("agency.txt", "stops.txt"), names.toSet())
        assertNotNull(GtfsArchive.openFile(dest, "agency.txt"))
    }

    @Test fun `flattens single wrapper directory`() {
        val dest = Files.createTempDirectory("d2")
        val names = GtfsArchive.extract(zip("gtfs/agency.txt" to "x", "gtfs/routes.txt" to "y"), dest)
        assertEquals(setOf("agency.txt", "routes.txt"), names.toSet())
    }

    @Test fun `rejects zip slip`() {
        val dest = Files.createTempDirectory("d3")
        assertFailsWith<GtfsParseException> {
            GtfsArchive.extract(zip("../evil.txt" to "x"), dest)
        }
    }
}
