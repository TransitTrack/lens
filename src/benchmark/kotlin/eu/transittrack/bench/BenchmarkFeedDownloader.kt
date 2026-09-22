package eu.transittrack.bench

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.readBytes

import eu.transittrack.gtfs.download.DownloadedFeed
import eu.transittrack.gtfs.download.FeedDownloader

/**
 * [FeedDownloader] that zips [BenchmarkGtfsFixture]'s in-memory CSV files in place of a real HTTP
 * download - mirrors `eu.transittrack.gtfs.support.FixtureDownloader`'s test-fixture approach, but
 * serves generated content instead of files on disk.
 */
class BenchmarkFeedDownloader(
    private val files: Map<String, String>,
) : FeedDownloader {
    override fun download(
        url: String,
        into: Path,
    ): DownloadedFeed {
        ZipOutputStream(Files.newOutputStream(into)).use { z ->
            files.toSortedMap().forEach { (name, content) ->
                z.putNextEntry(ZipEntry(name).apply { time = 0L })
                z.write(content.toByteArray())
                z.closeEntry()
            }
        }
        val bytes = into.readBytes()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return DownloadedFeed(into, sha, bytes.size.toLong())
    }
}
