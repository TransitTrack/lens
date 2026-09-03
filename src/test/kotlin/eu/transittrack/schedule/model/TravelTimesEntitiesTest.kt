package eu.transittrack.schedule.model

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
class TravelTimesEntitiesTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val travelTimes: TravelTimesForStopPathRepository,
) {
    @Test
    fun `travel times persist and query by pattern in index order`() {
        val feed = feeds.save(
            GtfsFeed("tt", "TT", null, "http://x/z.zip", null, true, null, FeedSource.API, Instant.now(), Instant.now()),
        )
        val rev = revisions
            .save(
                GtfsRevision(feedId = feed.id!!, status = GtfsRevisionStatus.READY, sourceUrl = "u", createdAt = Instant.now()),
            ).id!!
        val tp = patterns.save(
            TripPattern(rev, "K", "RA", null, 0, "H", "SHP", 2, 100.0, tripCount = 0),
        )
        travelTimes.save(TravelTimesForStopPath(rev, tp.id!!, 1, 90, 15))
        travelTimes.save(TravelTimesForStopPath(rev, tp.id!!, 0, null, null))

        val ordered = travelTimes.findByTripPatternOrdered(rev, tp.id!!)
        assertThat(ordered.map { it.stopPathIndex }).containsExactly(0, 1)
        assertThat(ordered[1].travelTimeSec).isEqualTo(90)
        assertThat(ordered[1].howSet).isEqualTo(TravelTimeSource.SCHEDULE)
    }
}
