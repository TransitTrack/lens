package eu.transittrack.support

import java.time.Instant

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

import eu.transittrack.avl.ingest.AvlFormat
import eu.transittrack.avl.match.AvlAssignmentMode
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlFeedTable

/** Inserts a bare `avl_feed` row with an explicit [id] and commits it (tests run outside any Spring transaction). */
fun Database.insertAvlFeed(
    id: Long,
    code: String,
    name: String = "feed",
    gtfsFeedCode: String = "g",
    url: String = "http://x",
    pollIntervalSec: Int = 30,
    assignmentMode: AvlAssignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR,
) {
    val now = Instant.now()
    transaction(this) {
        AvlFeedTable.insert {
            it[AvlFeedTable.id] = id
            it[AvlFeedTable.code] = code
            it[AvlFeedTable.name] = name
            it[AvlFeedTable.gtfsFeedCode] = gtfsFeedCode
            it[AvlFeedTable.url] = url
            it[format] = AvlFormat.GTFS_RT
            it[AvlFeedTable.pollIntervalSec] = pollIntervalSec
            it[AvlFeedTable.assignmentMode] = assignmentMode
            it[enabled] = true
            it[feedSource] = AvlFeedSourceKind.CONFIG
            it[createdAt] = now
            it[updatedAt] = now
        }
    }
}
