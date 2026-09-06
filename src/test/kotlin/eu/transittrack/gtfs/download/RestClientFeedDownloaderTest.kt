package eu.transittrack.gtfs.download

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasLength
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.io.TempDir

import eu.transittrack.http.HttpClientProperties

class RestClientFeedDownloaderTest {
    private lateinit var server: MockWebServer

    @TempDir
    lateinit var tmp: Path

    private fun downloader(maxBytes: Long = 1_000_000) =
        RestClientFeedDownloader(
            HttpClientProperties(
                connectTimeoutMs = 2000,
                readTimeoutMs = 2000,
                maxSizeBytes = maxBytes,
            ),
        )

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `downloads body and computes sha256`() {
        server.enqueue(MockResponse().setBody("PK-fake-zip-bytes"))
        val out = tmp.resolve("a.zip")
        val res = downloader().download(server.url("/g.zip").toString(), out)
        assertThat(res.byteSize).isEqualTo(17L)
        assertThat(res.byteSize).isEqualTo(Files.size(out))
        assertThat(res.sha256).hasLength(64)
        assertThat(res.sha256).isEqualTo(res.sha256.lowercase())
    }

    @Test
    fun `rejects oversize response`() {
        val big = Buffer().apply { write(ByteArray(5000)) }
        server.enqueue(MockResponse().setBody(big))
        val out = tmp.resolve("b.zip")
        assertFailure {
            downloader(maxBytes = 1000).download(server.url("/g.zip").toString(), out)
        }.isInstanceOf<FeedDownloadException>()
        assertThat(Files.exists(out)).isFalse()
    }

    @Test
    fun `rejects non-2xx`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val out = tmp.resolve("c.zip")
        assertFailure {
            downloader().download(server.url("/missing.zip").toString(), out)
        }.isInstanceOf<FeedDownloadException>()
        assertThat(Files.exists(out)).isFalse()
    }
}
