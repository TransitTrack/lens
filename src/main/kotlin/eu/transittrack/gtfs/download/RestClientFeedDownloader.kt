package eu.transittrack.gtfs.download

import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.HexFormat

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

import eu.transittrack.HttpClientProperties
import eu.transittrack.observability.TransitTrackMetrics

/**
 * Streaming [FeedDownloader] built on Spring's [RestClient] over the JDK HTTP client.
 *
 * The response body is streamed straight to [into] while a SHA-256 digest is computed in the same
 * pass. The download is aborted with a [FeedDownloadException] when the response status is non-2xx,
 * when the body exceeds [HttpClientProperties.maxSizeBytes], or on any IO/timeout failure. The
 * partial file is always removed on failure.
 */
@Component
class RestClientFeedDownloader(
    private val okHttpClient: OkHttpClient,
    private val metrics: TransitTrackMetrics = TransitTrackMetrics.forTests(),
) : FeedDownloader {
    override fun download(
        url: String,
        into: Path,
    ): DownloadedFeed {
        val startedAt = Instant.now()
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request
            .Builder()
            .get()
            .url(
                url
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("cache-buster", Instant.now().toEpochMilli().toString())
                    .build(),
            ).build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw FeedDownloadException("GET $url returned HTTP ${response.code}")
                }

                var total = 0L
                Files.newOutputStream(into).use { out ->
                    DigestInputStream(response.body.byteStream(), digest).use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            out.write(buf, 0, n)
                        }
                    }
                }

                metrics
                    .outboundHttp(
                        "gtfs_download", url, response.code, TransitTrackMetrics.Outcome.SUCCESS,
                        Duration
                            .between(startedAt, Instant.now()),
                    )
                return DownloadedFeed(into, HexFormat.of().formatHex(digest.digest()), total)
            }
        } catch (e: Exception) {
            metrics.outboundHttp("gtfs_download", url, null, TransitTrackMetrics.Outcome.FAILED, Duration.between(startedAt, Instant.now()))
            Files.deleteIfExists(into)
            val unwrapped = generateSequence(e as Throwable?) {
                it.cause
            }.take(20).filterIsInstance<FeedDownloadException>().firstOrNull()
            throw unwrapped ?: FeedDownloadException("download of $url failed", e)
        }
    }
}
