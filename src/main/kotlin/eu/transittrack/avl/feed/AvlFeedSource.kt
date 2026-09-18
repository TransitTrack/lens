package eu.transittrack.avl.feed

import eu.transittrack.avl.model.AvlFeed

/** Transport adapter: pull the current AVL payload for a feed. HTTP today; push/stream later. */
interface AvlFeedSource {
    fun fetch(feed: AvlFeed): RawAvlPayload
}
