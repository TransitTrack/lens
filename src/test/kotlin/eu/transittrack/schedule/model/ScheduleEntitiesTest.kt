package eu.transittrack.schedule.model

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired

@PostgresSliceTest
class ScheduleEntitiesTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val stopPaths: StopPathRepository,
    @Autowired val schedTrips: SchedTripRepository,
    @Autowired val scheduleTimes: ScheduleTimeRepository,
    @Autowired val blocks: BlockRepository,
) {
    private var rev: Long = 0

    @BeforeTest
    fun seed() {
        val feed = feeds.save(
            GtfsFeed(
                code = "sched-test", name = "Sched", description = null,
                url = "http://x/z.zip", pollingCron = null, enabled = true,
                autoActivate = null, source = FeedSource.API,
                createdAt = Instant.now(), updatedAt = Instant.now(),
            ),
        )
        rev = revisions.save(
            GtfsRevision(
                feedId = feed.id!!, status = GtfsRevisionStatus.READY,
                sourceUrl = "http://x/z.zip", createdAt = Instant.now(),
            ),
        ).id!!
    }

    @Test
    fun `schedule entities persist and query by revision`() {
        val tp = patterns.save(
            TripPattern(
                revisionId = rev, patternKey = "SHP|S1_to_S4|abc123", routeId = "RA",
                routeShortName = null,
                directionId = 0, headsign = "To S4", shapeId = "SHP", stopCount = 2,
                lengthM = 1500.0, tripCount = 1,
            ),
        )
        stopPaths.save(
            StopPath(
                revisionId = rev, tripPatternId = tp.id!!, stopPathIndex = 0, stopId = "S1",
                gtfsStopSeq = 1, lengthM = 0.0, pathGeometry = "[[17.0,51.1]]",
                pickupType = null, dropOffType = null, waitStop = true,
                scheduleAdherenceStop = true, layoverStop = true, breakTimeSec = null,
                typicalTravelTimeSec = null, typicalDwellTimeSec = null,
            ),
        )
        val st = schedTrips.save(
            SchedTrip(
                revisionId = rev, tripPatternId = tp.id!!, tripId = "T1", routeId = "RA",
                serviceId = "WK", directionId = 0, headsign = "To S4", tripShortName = null,
                startTimeSec = 28800, endTimeSec = 30600, frequencyBased = false, exactTimes = null,
            ),
        )
        scheduleTimes.save(
            ScheduleTime(
                revisionId = rev, schedTripId = st.id!!, stopPathIndex = 0,
                arrivalSec = 28800, departureSec = 28800, interpolated = false,
                schedTravelTimeSec = null, schedDwellTimeSec = 0,
            ),
        )
        blocks.save(
            Block(
                revisionId = rev, blockId = "B1", serviceId = "WK",
                startTimeSec = 28800, endTimeSec = 33000, tripCount = 2, routeIds = listOf("RA"),
            ),
        )

        assertEquals(1, patterns.findByRevisionId(rev).size)
        assertEquals("RA", patterns.findByPatternKey(rev, "SHP|S1_to_S4|abc123")!!.routeId)
        assertEquals(1, stopPaths.findByTripPatternOrdered(rev, tp.id!!).size)
        assertEquals("T1", schedTrips.findByTripId(rev, "T1")!!.tripId)
        assertEquals(1, scheduleTimes.findBySchedTripOrdered(rev, st.id!!).size)
        assertEquals(listOf("RA"), blocks.findByBlockId(rev, "B1").single().routeIds)
    }
}
