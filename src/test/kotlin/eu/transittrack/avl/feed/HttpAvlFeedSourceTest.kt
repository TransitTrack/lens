package eu.transittrack.avl.feed

import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer

import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.feed.AvlAssignmentMode
import eu.transittrack.feed.AvlFormat
import eu.transittrack.http.HttpClientProperties

class HttpAvlFeedSourceTest {
    private lateinit var server: MockWebServer

    @BeforeTest
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun stop() = server.shutdown()

    private fun feed(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ) = AvlFeed(
        code = "f",
        name = "F",
        gtfsFeedCode = "g",
        url = url,
        format = AvlFormat.GTFS_RT,
        pollIntervalSec = 15,
        assignmentMode = AvlAssignmentMode.FULL_INFERENCE,
        enabled = true,
        headers = headers.ifEmpty { null },
        source = AvlFeedSourceKind.CONFIG,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private val source = HttpAvlFeedSource(HttpClientProperties())

    @Test
    fun `fetches bytes and sends configured headers`() {
        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3))))
        val p = source.fetch(feed(server.url("/vp.pb").toString(), mapOf("X-Api-Key" to "k")))
        assertThat(p.bytes.toList()).isEqualTo(listOf<Byte>(1, 2, 3))
        assertThat(server.takeRequest().getHeader("X-Api-Key")).isEqualTo("k")
    }

    @Test
    fun `non-2xx throws AvlFetchException`() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertFailure { source.fetch(feed(server.url("/vp.pb").toString())) }
            .isInstanceOf(eu.transittrack.avl.ingest.AvlFetchException::class)
    }

    @Test
    fun `oversize body throws`() {
        val big = HttpClientProperties(maxSizeBytes = 2)
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(10))))
        assertFailure { HttpAvlFeedSource(big).fetch(feed(server.url("/vp.pb").toString())) }
            .isInstanceOf(eu.transittrack.avl.ingest.AvlFetchException::class)
    }
}
