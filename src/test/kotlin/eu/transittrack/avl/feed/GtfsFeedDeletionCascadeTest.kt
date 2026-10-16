package eu.transittrack.avl.feed

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.VehicleState
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

/**
 * End-to-end check that deleting a GTFS feed takes every piece of data rooted at it with it:
 * its revisions (and everything `gtfs_revision` cascades to, proven elsewhere by the schema
 * itself), its linked [AvlFeed] (via [AvlFeedCleanupListener]), and `vehicle_state`, which only
 * gained a cascading FK in `0013-feed-delete-cascade-gaps.yaml`.
 */
@PostgresSliceTest
@Import(GtfsFeedService::class, AvlFeedCleanupListener::class)
class GtfsFeedDeletionCascadeTest(
    @Autowired val feedService: GtfsFeedService,
    @Autowired val revisionRepo: GtfsRevisionRepository,
    @Autowired val avlFeedRepo: AvlFeedRepository,
    @Autowired val vehicleStateRepo: VehicleStateRepository,
) : PostgresPerMethodTest() {
    @Test
    fun `deleting a feed removes its revision, avl feed, and vehicle state`() {
        val feed = feedService.register(FeedInput("w", "W", null, "http://x/z.zip", null))

        val revision =
            revisionRepo.save(
                GtfsRevision(
                    feedId = feed.id!!,
                    status = GtfsRevisionStatus.ACTIVE,
                    sourceUrl = feed.url,
                ),
            )

        val now = Instant.now()
        val avlFeed =
            avlFeedRepo.save(
                AvlFeed(
                    code = "w",
                    name = "W vehicle positions",
                    gtfsFeedCode = "w",
                    url = "https://w.test/vp.pb",
                    format = AvlFormat.GTFS_RT,
                    pollIntervalSec = 30,
                    assignmentMode = AvlAssignmentMode.FULL_INFERENCE,
                    enabled = true,
                    headers = null,
                    source = AvlFeedSourceKind.API,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

        vehicleStateRepo.save(
            VehicleState(
                feedId = avlFeed.id!!,
                vehicleId = "v1",
                vehicleLabel = null,
                reportTs = now,
                lat = 1.0,
                lon = 1.0,
                bearing = null,
                speedMps = null,
                occupancyStatus = null,
                matched = false,
                stale = false,
                consecutiveFailures = 0,
                revisionId = null,
                tripRowId = null,
                blockPk = null,
                tripPatternId = null,
                stopPathIndex = null,
                distanceAlongTripM = null,
                scheduleAdherenceSec = null,
                snappedLat = null,
                snappedLon = null,
                updatedAt = now,
            ),
        )

        feedService.delete("w")

        assertThat(revisionRepo.findById(revision.id!!).orElse(null)).isNull()
        assertThat(avlFeedRepo.findByCode("w")).isNull()
        assertThat(vehicleStateRepo.findByFeedId(avlFeed.id!!)).isEmpty()
    }
}
