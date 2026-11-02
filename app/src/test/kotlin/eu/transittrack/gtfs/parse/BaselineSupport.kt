package eu.transittrack.gtfs.parse

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.reflect.full.memberProperties

import eu.transittrack.gtfs.model.RevisionScoped

/**
 * Shared helpers for the parsing baseline: the golden files under `src/test/resources/gtfs-baseline/`
 * were captured from the MobilityData pipeline, and [BaselineEquivalenceTest] checks the in-house
 * reader produces the same mapped entities.
 */
object BaselineSupport {
    val fixturesDir: Path = Path("src/test/resources/gtfs")
    val baselineDir: Path = Path("src/test/resources/gtfs-baseline")

    fun zipFixture(
        fixture: String,
        work: Path,
    ): Path {
        val zip = work.resolve("$fixture.zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { z ->
            fixturesDir
                .resolve(fixture)
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

    /** A single [GtfsRecord] parsed from a header line and one data line. */
    fun record(
        header: String,
        row: String,
        fileName: String = "t.txt",
    ): GtfsRecord {
        var out: GtfsRecord? = null
        GtfsCsv.forEachRecord("$header\n$row\n".byteInputStream(), fileName) { out = it }
        return out!!
    }

    /** Every property except the DB-assigned `id`, sorted by name, values as `toString()`. */
    fun snapshot(entity: RevisionScoped): Map<String, String?> =
        entity::class
            .memberProperties
            .filter { it.name != "id" }
            .sortedBy { it.name }
            .associateTo(LinkedHashMap()) { p -> p.name to p.getter.call(entity)?.toString() }
}
