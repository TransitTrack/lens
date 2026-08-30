package eu.transittrack.gtfs.support

import eu.transittrack.gtfs.download.DownloadedFeed
import eu.transittrack.gtfs.download.FeedDownloader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes

/**
 * Test [FeedDownloader] that zips a fixture folder under
 * `src/test/resources/gtfs/<fixture>/` into the requested target path and serves it
 * as the "downloaded" feed. Entries are added in a stable (sorted) order so the
 * archive bytes — and therefore the SHA-256 — are deterministic for a given fixture.
 */
class FixtureDownloader(private val fixture: String) : FeedDownloader {
    override fun download(url: String, into: Path): DownloadedFeed {
        val dir = Path("src/test/resources/gtfs/$fixture")
        ZipOutputStream(Files.newOutputStream(into)).use { z ->
            dir.listDirectoryEntries()
                .filter { it.isRegularFile() }
                .sortedBy { it.name }
                .forEach { f ->
                    z.putNextEntry(ZipEntry(f.name))
                    z.write(f.readBytes())
                    z.closeEntry()
                }
        }
        val bytes = into.readBytes()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return DownloadedFeed(into, sha, bytes.size.toLong())
    }
}
