package eu.transittrack.gtfs.export

import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.util.Optional
import kotlin.io.path.name
import kotlin.streams.toList
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isNotEmpty
import org.hamcrest.Matchers.containsString
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus

/**
 * Standalone [MockMvcBuilders.standaloneSetup] rather than `@WebMvcTest` / `TestRestTemplate`:
 * Spring Boot 4.1's modular test classpath here ships neither the `@WebMvcTest` slice nor
 * `TestRestTemplate` (`spring-test`'s `MockMvc` is present). Standalone needs no application context.
 */
class RevisionExportControllerTest {
    private val serializer: GtfsSerializer = mock()
    private val revisions: GtfsRevisionRepository = mock()
    private val feeds: GtfsFeedRepository = mock()
    private val mvc =
        MockMvcBuilders
            .standaloneSetup(RevisionExportController(serializer, revisions, feeds))
            .build()

    private fun feed() =
        GtfsFeed(
            "stpt",
            "F",
            null,
            "u",
            null,
            true,
            null,
            FeedSource.CONFIG,
            Instant.now(),
            Instant.now(),
        ).apply { id = 1 }

    @Test
    fun `streams a zip with an attachment header`() {
        val rev =
            GtfsRevision(feedId = 1, status = GtfsRevisionStatus.ACTIVE, sourceUrl = "u").apply { id = 5 }
        whenever(revisions.findById(5)).thenReturn(Optional.of(rev))
        whenever(feeds.findById(1)).thenReturn(Optional.of(feed()))
        whenever(serializer.serialize(any(), any())).thenAnswer {
            (it.arguments[1] as OutputStream).write("PK".toByteArray())
        }

        mvc
            .perform(get("/api/revisions/5/gtfs.zip"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", containsString("attachment")))
            .andExpect(header().string("Content-Disposition", containsString("stpt-rev5.zip")))
            .andExpect(content().contentType("application/zip"))
            .andExpect(content().bytes("PK".toByteArray()))

        // The body is streamed straight from the on-disk spool file (FileSystemResource opens it
        // lazily), so the file is still present right after the request; ExportTempFileSweeper
        // reaps stragglers asynchronously.
        val leftovers =
            Files.list(Paths.get(System.getProperty("java.io.tmpdir"))).use { paths ->
                paths.toList().filter { it.name.startsWith("gtfs-export-5-") }
            }
        assertThat(leftovers).isNotEmpty()
        leftovers.forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun `404 for unknown revision`() {
        whenever(revisions.findById(999)).thenReturn(Optional.empty())
        mvc
            .perform(get("/api/revisions/999/gtfs.zip"))
            .andExpect(status().isNotFound)
    }
}
