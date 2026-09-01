package eu.transittrack.gtfs.model

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired

@PostgresSliceTest
class ShapeEntitiesTest(
    @Autowired val shapes: GtfsShapeRepository,
    @Autowired val points: GtfsShapePointRepository,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
) {
    /** Seed a real feed + revision so the mandated FK on `revision_id` is satisfied (PF-11). */
    private fun seedRevisionId(): Long {
        val feed = feeds.save(
            GtfsFeed(
                code = "f1", name = "F1", description = null, url = "http://x/z.zip",
                pollingCron = null, enabled = true, autoActivate = null, source = FeedSource.API,
                createdAt = Instant.now(), updatedAt = Instant.now(),
            )
        )
        val revision = revisions.save(
            GtfsRevision(feedId = feed.id!!, status = GtfsRevisionStatus.PENDING, sourceUrl = "u")
        )
        return revision.id!!
    }

    @Test
    fun `shape and ordered points persist`() {
        val rev = seedRevisionId()

        shapes.save(GtfsShape(revisionId = rev, shapeId = "SH1", pointCount = 2, lengthM = 12.5))
        points.save(GtfsShapePoint(rev, "SH1", 51.1, 17.0, 2, 10.0))
        points.save(GtfsShapePoint(rev, "SH1", 51.0, 17.0, 1, 0.0))

        val ordered = points.findByShapeId(rev, "SH1")
        assertEquals(listOf(1, 2), ordered.map { it.shapePtSequence })
        assertEquals(2, shapes.findByShapeId(rev, "SH1")!!.pointCount)
    }
}
