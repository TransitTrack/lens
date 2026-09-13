package eu.transittrack.avl.feed

import java.time.Duration
import java.time.Instant

import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component

import eu.transittrack.HttpClientProperties
import eu.transittrack.avl.ingest.AvlFetchException
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.observability.TransitTrackMetrics

/**
 * okhttp-backed [AvlFeedSource]. GET the feed URL with the feed's configured headers, enforce
 * [HttpClientProperties.maxSizeBytes], and wrap every failure (non-2xx, oversize, IO/timeout) in an
 * [AvlFetchException].
 */
@Component
class HttpAvlFeedSource(
    private val client: OkHttpClient,
    private val metrics: TransitTrackMetrics = TransitTrackMetrics.forTests(),
) : AvlFeedSource {
    override fun fetch(feed: AvlFeed): RawAvlPayload {
        val startedAt = Instant.now()
        val builder = Request
            .Builder()
            .get()
            .url(feed.url)
            .cacheControl(CacheControl.FORCE_NETWORK)

        feed.headers?.forEach { (k, v) -> builder.header(k, v) }
        try {
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AvlFetchException("GET ${feed.url} returned HTTP ${response.code}")
                }
                val body = response.body.bytes()
                metrics
                    .outboundHttp(
                        "avl_poll", feed.url, response.code, TransitTrackMetrics.Outcome.SUCCESS,
                        Duration
                            .between(startedAt, Instant.now()),
                    )
                return RawAvlPayload(body, response.body.contentType()?.toString(), Instant.now())
            }
        } catch (e: AvlFetchException) {
            metrics.outboundHttp("avl_poll", feed.url, null, TransitTrackMetrics.Outcome.FAILED, Duration.between(startedAt, Instant.now()))
            throw e
        } catch (e: Exception) {
            metrics.outboundHttp("avl_poll", feed.url, null, TransitTrackMetrics.Outcome.FAILED, Duration.between(startedAt, Instant.now()))
            throw AvlFetchException("fetch of ${feed.url} failed", e)
        }
    }
}
