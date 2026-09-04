package eu.transittrack.avl.feed

import java.time.Instant

import eu.transittrack.avl.model.AvlFeed

class RawAvlPayload(
    val bytes: ByteArray,
    val contentType: String?,
    val fetchedAt: Instant,
)

/** Transport adapter: pull the current AVL payload for a feed. HTTP today; push/stream later. */
interface AvlFeedSource {
    fun fetch(feed: AvlFeed): RawAvlPayload
}
