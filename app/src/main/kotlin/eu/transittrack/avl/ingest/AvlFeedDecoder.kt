package eu.transittrack.avl.ingest

import eu.transittrack.avl.feed.Payload

interface AvlFeedDecoder {
    val format: AvlFormat

    fun decode(
        payload: Payload,
        feed: FeedDescriptor,
    ): List<AvlReport>
}
