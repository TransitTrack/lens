package eu.transittrack.gtfs.read

import org.springframework.stereotype.Component

import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.RevisionService

/**
 * Resolves the concrete `gtfs_revision` id a read query should target.
 *
 * An explicit `revisionId` argument wins verbatim; otherwise the feed's current `ACTIVE` revision
 * is used. A missing feed is an [IllegalArgumentException]; a feed with no active revision is an
 * [IllegalStateException]. Both map to a client-facing `BAD_REQUEST` GraphQL error via
 * `GtfsGraphQlConfig`.
 */
@Component
class RevisionResolver(
    private val feeds: GtfsFeedRepository,
    private val revisionService: RevisionService,
) {
    fun resolve(
        feedCode: String,
        revisionId: String?,
    ): Long {
        if (revisionId != null) return revisionId.toLong()
        val feed = feeds.findByCode(feedCode) ?: throw IllegalArgumentException("no feed '$feedCode'")
        return revisionService.activeRevisionId(feed.id!!)
            ?: throw IllegalStateException("feed '$feedCode' has no active revision")
    }
}
