package eu.transittrack.gtfs.download

import java.nio.file.Path

/** Result of a successful feed download. */
data class DownloadedFeed(val path: Path, val sha256: String, val byteSize: Long)

/** Downloads a GTFS feed archive from [url] into the file [into], streaming to disk. */
interface FeedDownloader {
    fun download(url: String, into: Path): DownloadedFeed
}

/** Raised when a feed download fails: non-2xx status, oversize body, timeout or any IO error. */
class FeedDownloadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
