package eu.transittrack.gtfs.download

import eu.transittrack.gtfs.config.GtfsProperties
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class RestClientFeedDownloaderTest {

    private lateinit var server: MockWebServer

    @TempDir
    lateinit var tmp: Path

    private fun downloader(maxBytes: Long = 1_000_000) = RestClientFeedDownloader(
        GtfsProperties(
            download = GtfsProperties.Download(
                connectTimeoutMs = 2000,
                readTimeoutMs = 2000,
                maxSizeBytes = maxBytes,
            )
        )
    )

    @BeforeTest fun setUp() { server = MockWebServer(); server.start() }
    @AfterTest fun tearDown() { server.shutdown() }

    @Test fun `downloads body and computes sha256`() {
        server.enqueue(MockResponse().setBody("PK-fake-zip-bytes"))
        val out = tmp.resolve("a.zip")
        val res = downloader().download(server.url("/g.zip").toString(), out)
        assertEquals(17, res.byteSize)
        assertEquals(Files.size(out), res.byteSize)
        assertEquals(64, res.sha256.length)
        assertEquals(res.sha256, res.sha256.lowercase())
    }

    @Test fun `rejects oversize response`() {
        val big = Buffer().apply { write(ByteArray(5000)) }
        server.enqueue(MockResponse().setBody(big))
        val out = tmp.resolve("b.zip")
        assertFailsWith<FeedDownloadException> {
            downloader(maxBytes = 1000).download(server.url("/g.zip").toString(), out)
        }
        assertFalse(Files.exists(out))
    }

    @Test fun `rejects non-2xx`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val out = tmp.resolve("c.zip")
        assertFailsWith<FeedDownloadException> {
            downloader().download(server.url("/missing.zip").toString(), out)
        }
        assertFalse(Files.exists(out))
    }
}
