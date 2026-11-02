package eu.transittrack.gtfs.parse

import java.io.Closeable
import java.io.InputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import eu.transittrack.ParseException

/**
 * Read-only view of a GTFS archive. Entries are addressed by base name, so a feed zipped with a
 * single wrapping folder (`gtfs/stops.txt`) resolves the same as one zipped at the root. When a name
 * appears at several depths the shallowest copy wins (the one the analyzer validates).
 */
class GtfsZip(
    path: Path,
) : Closeable {
    private val zip = ZipFile(path.toFile())
    private val entries: Map<String, List<ZipEntry>>

    init {
        try {
            entries =
                zip
                    .entries()
                    .asSequence()
                    .filterNot { it.isDirectory }
                    .onEach { e ->
                        if (e.name.split('/', '\\').any { it == ".." }) throw ParseException("unsafe zip entry '${e.name}'")
                    }.groupBy { it.name.substringAfterLast('/') }
                    .mapValues { (_, candidates) -> shallowest(candidates) }
        } catch (e: Exception) {
            zip.close()
            throw e
        }
    }

    val fileNames: Set<String> get() = entries.keys

    fun open(fileName: String): InputStream? =
        entries[fileName]?.let { top ->
            if (top.size > 1) throw ParseException("ambiguous zip entries for '$fileName': ${top.map { it.name }}")
            zip.getInputStream(top.single())
        }

    override fun close() = zip.close()

    private companion object {
        /** A name repeated in a sub-folder resolves to the copies closest to the root; several left is ambiguous. */
        fun shallowest(candidates: List<ZipEntry>): List<ZipEntry> {
            val depth = candidates.minOf { it.name.count { c -> c == '/' } }
            return candidates.filter { it.name.count { c -> c == '/' } == depth }
        }
    }
}
