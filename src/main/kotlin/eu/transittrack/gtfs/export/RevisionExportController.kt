package eu.transittrack.gtfs.export

import java.nio.file.Files

import org.springframework.core.io.FileSystemResource
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
 * `GtfsRevisionValidator`), then the response streams that file via [FileSystemResource], which
 * opens the file lazily only when the message converter actually writes the body — so a `HEAD`
 * request or a client that disconnects before the body is streamed leaks neither an FD nor the
 * temp file. Spooling before the `200` means a serialization failure surfaces as a clean `500`
 * rather than a truncated body. Leftover temp files (client aborted mid-stream) are reaped by
 * [ExportTempFileSweeper].
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
        try {
            Files.newOutputStream(tmp).use { serializer.serialize(id, it) }
        } catch (e: Exception) {
            runCatching { Files.deleteIfExists(tmp) }
            throw e
        }

        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$feedCode-rev$id.zip\"")
            .contentLength(Files.size(tmp))
            .body(FileSystemResource(tmp))
    }
}
