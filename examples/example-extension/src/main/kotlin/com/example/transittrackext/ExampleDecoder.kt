package com.example.transittrackext

import eu.transittrack.AvlFormat
import eu.transittrack.avl.feed.RawAvlPayload
import eu.transittrack.avl.ingest.AvlFeedDecoder
import eu.transittrack.avl.ingest.AvlReport
import eu.transittrack.avl.ingest.FeedDescriptor

/** Reuses [AvlFormat.STPT] only because it's an existing enum value and this class isn't
 * exercising real format dispatch — it exists purely to prove the auto-configuration /
 * classpath-discovery chain (see [eu.transittrack.extension.ExtensionLoadingTest]), not as a
 * second STPT decoder. */
class ExampleDecoder : AvlFeedDecoder {
    override val format = AvlFormat.STPT

    override fun decode(
        payload: RawAvlPayload,
        feed: FeedDescriptor,
    ): List<AvlReport> = emptyList()
}
