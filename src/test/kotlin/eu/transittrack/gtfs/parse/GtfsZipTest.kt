package eu.transittrack.gtfs.parse

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull

import eu.transittrack.ParseException

class GtfsZipTest {
    private val work: Path = Files.createTempDirectory("gtfs-zip-")

    @AfterTest
    fun cleanup() {
        work.toFile().deleteRecursively()
    }

    private fun zip(vararg entries: Pair<String, String>): Path {
        val path = work.resolve("feed.zip")
        ZipOutputStream(Files.newOutputStream(path)).use { z ->
            entries.forEach { (name, body) ->
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray())
                z.closeEntry()
            }
        }
        return path
    }

    @Test
    fun `resolves entries at root`() {
        GtfsZip(zip("stops.txt" to "stop_id\nS1\n")).use { z ->
            assertThat(z.fileNames).isEqualTo(setOf("stops.txt"))
            assertThat(z.open("stops.txt")).isNotNull()
        }
    }

    @Test
    fun `resolves entries inside a wrapping folder`() {
        GtfsZip(zip("gtfs/stops.txt" to "stop_id\nS1\n")).use { z ->
            assertThat(z.fileNames).isEqualTo(setOf("stops.txt"))
            assertThat(z.open("stops.txt")!!.use { String(it.readBytes()) }).isEqualTo("stop_id\nS1\n")
        }
    }

    @Test
    fun `missing file returns null`() {
        GtfsZip(zip("stops.txt" to "stop_id\n")).use { z ->
            assertThat(z.open("trips.txt")).isNull()
        }
    }

    @Test
    fun `rejects unsafe entry names`() {
        val path = zip("../x.txt" to "x")
        assertFailure { GtfsZip(path) }.isInstanceOf<ParseException>()
    }

    @Test
    fun `prefers the shallowest entry when a name repeats in a sub-folder`() {
        GtfsZip(zip("old/stops.txt" to "old", "stops.txt" to "root")).use { z ->
            assertThat(z.open("stops.txt")!!.use { String(it.readBytes()) }).isEqualTo("root")
        }
    }

    @Test
    fun `rejects opening a name that appears twice at the same depth`() {
        GtfsZip(zip("a/stops.txt" to "a", "b/stops.txt" to "b")).use { z ->
            assertFailure { z.open("stops.txt") }.isInstanceOf<ParseException>()
        }
    }

    @Test
    fun `unrelated duplicate names do not block other files`() {
        GtfsZip(zip("a/readme.txt" to "a", "b/readme.txt" to "b", "stops.txt" to "s")).use { z ->
            assertThat(z.open("stops.txt")!!.use { String(it.readBytes()) }).isEqualTo("s")
        }
    }

    @Test
    fun `allows double dots inside a file name`() {
        GtfsZip(zip("notes..txt" to "x", "stops.txt" to "s")).use { z ->
            assertThat(z.open("stops.txt")).isNotNull()
        }
    }
}
