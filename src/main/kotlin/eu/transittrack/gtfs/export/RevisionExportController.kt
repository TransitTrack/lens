package eu.transittrack.gtfs.export

import java.io.PipedInputStream
import java.io.PipedOutputStream

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
 * REST download of any revision's RAW GTFS zip. [GtfsSerializer] writes the zip into a
 * [PipedOutputStream] on a worker thread while the HTTP response thread drains the paired
 * [PipedInputStream] — the whole feed never sits in the heap at once.
 *
 * Uses only `spring-web` / `spring-core` types (no `spring-webmvc` `StreamingResponseBody`): the
 * web MVC stack is present at runtime but not on this module's compile classpath.
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

        val sink = PipedOutputStream()
        val source = PipedInputStream(sink, DEFAULT_BUFFER_SIZE)
        Thread({
            sink.use { serializer.serialize(id, it) }
        }, "gtfs-export-$id").apply { isDaemon = true }.start()

        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$feedCode-rev$id.zip\"")
            .body(InputStreamResource(source))
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}
