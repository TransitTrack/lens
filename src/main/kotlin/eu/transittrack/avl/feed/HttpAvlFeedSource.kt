package eu.transittrack.avl.feed

import java.time.Instant
import java.util.concurrent.TimeUnit

import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component

import eu.transittrack.avl.AvlProperties
import eu.transittrack.avl.ingest.AvlFetchException
import eu.transittrack.avl.model.AvlFeed

/**
 * okhttp-backed [AvlFeedSource]. GET the feed URL with the feed's configured headers, enforce
 * [AvlProperties.Http.maxSizeBytes], and wrap every failure (non-2xx, oversize, IO/timeout) in an
 * [AvlFetchException].
 */
@Component
class HttpAvlFeedSource(
    private val props: AvlProperties,
) : AvlFeedSource {
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(props.http.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(props.http.readTimeoutMs, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .build()

    override fun fetch(feed: AvlFeed): RawAvlPayload {
        val builder =
            Request
                .Builder()
                .get()
                .url(feed.url)
                .header("User-Agent", props.http.userAgent)
        feed.headers?.forEach { (k, v) -> builder.header(k, v) }
        try {
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AvlFetchException("GET ${feed.url} returned HTTP ${response.code}")
                }
                val body = response.body.bytes()
                if (body.size.toLong() > props.http.maxSizeBytes) {
                    throw AvlFetchException(
                        "AVL payload from ${feed.url} exceeds ${props.http.maxSizeBytes} bytes",
                    )
                }
                return RawAvlPayload(body, response.body.contentType()?.toString(), Instant.now())
            }
        } catch (e: AvlFetchException) {
            throw e
        } catch (e: Exception) {
            throw AvlFetchException("fetch of ${feed.url} failed", e)
        }
    }
}
