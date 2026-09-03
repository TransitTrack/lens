package eu.transittrack.gtfs.ingest

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

import eu.transittrack.exception.ParseException

object GtfsArchive {
    fun extract(
        zip: Path,
        destDir: Path,
    ): List<String> {
        Files.createDirectories(destDir)
        val normalizedDest = destDir.normalize()
        val extracted = mutableListOf<String>()
        ZipInputStream(Files.newInputStream(zip)).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    if (entry.name.contains("..")) {
                        throw ParseException("unsafe zip entry '${entry.name}'")
                    }
                    val cleanName = entry.name.substringAfterLast('/').ifEmpty { entry.name }
                    val target = normalizedDest.resolve(cleanName).normalize()
                    if (!target.startsWith(normalizedDest)) {
                        throw ParseException("unsafe zip entry '${entry.name}'")
                    }
                    Files.newOutputStream(target).use { zin.copyTo(it) }
                    extracted += cleanName
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        return extracted
    }

    fun openFile(
        destDir: Path,
        name: String,
    ): InputStream? {
        val path = destDir.resolve(name)
        return if (Files.exists(path)) Files.newInputStream(path) else null
    }
}
