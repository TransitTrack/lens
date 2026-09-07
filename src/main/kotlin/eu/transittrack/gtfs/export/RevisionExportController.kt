package eu.transittrack.gtfs.export

import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository

/**
 * REST download of any revision's RAW GTFS zip.
 *
 * [GtfsSerializer] spools the whole zip to a temp file up-front (same approach as
 * `GtfsRevisionValidator`), then the response streams that file. Spooling before the `200` means a
 * serialization failure surfaces as a clean `500` rather than a truncated body, and — unlike a
 * piped producer thread — a client that aborts the download mid-stream cannot leave a worker parked
 * on `jdbc.query` holding a DB connection. The temp file is deleted when the response stream closes
 * (with `deleteOnExit` as a backstop).
 *
 * Uses only `spring-web` / `spring-core` types (no `spring-webmvc` `StreamingResponseBody`): the web
 * MVC stack is present at runtime but not on this module's compile classpath.
 */
@RestController
class RevisionExportController(
    private val serializer: GtfsSerializer,
    private val revisions: GtfsRevisionRepository,
    private val feeds: GtfsFeedRepository,
) {
    @GetMapping("/api/revisions/{id}/gtfs.zip", produces = ["application/zip"])
    fun export(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        val rev =
            revisions.findById(id).orElse(null)
                ?: return ResponseEntity.notFound().build()
        val feedCode = feeds.findById(rev.feedId).map { it.code }.orElse("feed")

        val tmp = Files.createTempFile("gtfs-export-$id-", ".zip")
        tmp.toFile().deleteOnExit()
        try {
            Files.newOutputStream(tmp).use { serializer.serialize(id, it) }
        } catch (e: Exception) {
            runCatching { Files.deleteIfExists(tmp) }
            throw e
        }

        val body = InputStreamResource(deleteOnCloseStream(tmp))
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$feedCode-rev$id.zip\"")
            .contentLength(Files.size(tmp))
            .body(body)
    }

    /** Stream over [path] that deletes the file once the consumer closes it. */
    private fun deleteOnCloseStream(path: Path): InputStream =
        object : FilterInputStream(Files.newInputStream(path)) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    Files.deleteIfExists(path)
                }
            }
        }
}
