package eu.transittrack.gtfs.download

import eu.transittrack.gtfs.config.GtfsProperties
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

/**
 * Streaming [FeedDownloader] built on Spring's [RestClient] over the JDK HTTP client.
 *
 * The response body is streamed straight to [into] while a SHA-256 digest is computed in the
 * same pass. The download is aborted with a [FeedDownloadException] when the response status is
 * non-2xx, when the body exceeds [GtfsProperties.Download.maxSizeBytes], or on any IO/timeout
 * failure. The partial file is always removed on failure.
 */
@Component
class RestClientFeedDownloader(props: GtfsProperties) : FeedDownloader {

    private val maxBytes: Long = props.download.maxSizeBytes

    private val client: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(props.download.connectTimeoutMs))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
            ).apply { setReadTimeout(Duration.ofMillis(props.download.readTimeoutMs)) }
        )
        .defaultHeader("User-Agent", props.download.userAgent)
        .build()

    override fun download(url: String, into: Path): DownloadedFeed {
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            return client.get().uri(url).exchange { _, response ->
                if (!response.statusCode.is2xxSuccessful) {
                    throw FeedDownloadException("GET $url returned HTTP ${response.statusCode.value()}")
                }
                var total = 0L
                Files.newOutputStream(into).use { out ->
                    DigestInputStream(response.body, digest).use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) {
                                throw FeedDownloadException("feed at $url exceeds max size of $maxBytes bytes")
                            }
                            out.write(buf, 0, n)
                        }
                    }
                }
                DownloadedFeed(into, HexFormat.of().formatHex(digest.digest()), total)
            }
        } catch (e: Exception) {
            Files.deleteIfExists(into)
            val unwrapped = generateSequence(e as Throwable?) { it.cause }
                .take(20)
                .filterIsInstance<FeedDownloadException>()
                .firstOrNull()
            throw unwrapped ?: FeedDownloadException("download of $url failed", e)
        }
    }
}
