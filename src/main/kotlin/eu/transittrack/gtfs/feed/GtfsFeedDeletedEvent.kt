package eu.transittrack.gtfs.feed

/**
 * Published after a `gtfs_feed` row is deleted. `avl_feed` rows are linked to it only by a shared
 * `code` string (no FK/JPA relationship — see `AvlFeed.gtfsFeedCode`), so consumers that own
 * per-feed state keyed off that code must clean up on this event rather than relying on a cascade.
 */
data class GtfsFeedDeletedEvent(
    val code: String,
)
