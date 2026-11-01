package eu.transittrack.support

import java.time.Instant

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeedTable
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.GtfsRevisionTable

/** Inserts a bare `gtfs_feed` row with an explicit [id] and commits it. */
fun Database.insertGtfsFeed(
    id: Long,
    code: String,
    name: String = code.uppercase(),
) {
    val now = Instant.now()
    transaction(this) {
        GtfsFeedTable.insert {
            it[GtfsFeedTable.id] = id
            it[GtfsFeedTable.code] = code
            it[GtfsFeedTable.name] = name
            it[url] = "x"
            it[enabled] = true
            it[feedSource] = FeedSource.CONFIG
            it[createdAt] = now
            it[updatedAt] = now
        }
    }
}

/** Inserts a bare `gtfs_revision` row with an explicit [id] and commits it. */
fun Database.insertGtfsRevision(
    id: Long,
    feedId: Long,
    status: GtfsRevisionStatus = GtfsRevisionStatus.ACTIVE,
) {
    transaction(this) {
        GtfsRevisionTable.insert {
            it[GtfsRevisionTable.id] = id
            it[GtfsRevisionTable.feedId] = feedId
            it[GtfsRevisionTable.status] = status
            it[sourceUrl] = "x"
            it[createdAt] = Instant.now()
        }
    }
}
