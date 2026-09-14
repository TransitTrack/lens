package eu.transittrack.schedule.model

import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class ScheduleEntitiesTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val stopPaths: StopPathRepository,
    @Autowired val trips: TripRepository,
    @Autowired val scheduleTimes: ScheduleTimeRepository,
    @Autowired val blocks: BlockRepository,
) : PostgresPerMethodTest() {
    private var rev: Long = 0

    @BeforeTest
    fun seed() {
        val feed =
            feeds.save(
                GtfsFeed(
                    code = "sched-test",
                    name = "Sched",
                    description = null,
                    url = "http://x/z.zip",
                    pollingCron = null,
                    enabled = true,
                    autoActivate = null,
                    source = FeedSource.API,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
        rev =
            revisions
                .save(
                    GtfsRevision(
                        feedId = feed.id!!,
                        status = GtfsRevisionStatus.READY,
                        sourceUrl = "http://x/z.zip",
                        createdAt = Instant.now(),
                    ),
                ).id!!
    }

    @Test
    fun `schedule entities persist and query by revision`() {
        val tp =
            patterns.save(
                TripPattern(
                    revisionId = rev,
                    patternKey = "SHP|S1_to_S4|abc123",
                    routeId = "RA",
                    routeShortName = null,
                    directionId = 0,
                    headsign = "To S4",
                    shapeId = "SHP",
                    stopCount = 2,
                    lengthM = 1500.0,
                    tripCount = 1,
                ),
            )
        stopPaths.save(
            StopPath(
                revisionId = rev,
                tripPatternId = tp.id!!,
                stopPathIndex = 0,
                stopId = "S1",
                stopSeq = 1,
                lengthM = 0.0,
                pathGeometry = "[[17.0,51.1]]",
                pickupType = null,
                dropOffType = null,
                waitStop = true,
                scheduleAdherenceStop = true,
                layoverStop = true,
                breakTimeSec = null,
            ),
        )
        val st =
            trips.save(
                Trip(
                    revisionId = rev,
                    routeId = "RA",
                    serviceId = "WK",
                    tripId = "T1",
                    tripHeadsign = "To S4",
                    tripShortName = null,
                    directionId = 0,
                    blockId = null,
                    shapeId = "SHP",
                    wheelchairAccessible = null,
                    bikesAllowed = null,
                    tripPatternId = tp.id!!,
                    startTimeSec = 28800,
                    endTimeSec = 30600,
                    frequencyBased = false,
                    noSchedule = false,
                ),
            )
        scheduleTimes.save(
            ScheduleTime(
                revisionId = rev,
                tripId = st.id!!,
                stopPathIndex = 0,
                arrivalSec = 28800,
                departureSec = 28800,
                interpolated = false,
                schedTravelTimeSec = null,
                schedDwellTimeSec = 0,
            ),
        )
        blocks.save(
            Block(
                revisionId = rev,
                blockId = "B1",
                serviceId = "WK",
                startTimeSec = 28800,
                endTimeSec = 33000,
                tripCount = 2,
                routeIds = listOf("RA"),
            ),
        )

        assertThat(patterns.findByRevisionId(rev)).hasSize(1)
        assertThat(patterns.findByPatternKey(rev, "SHP|S1_to_S4|abc123")!!.routeId).isEqualTo("RA")
        assertThat(stopPaths.findByTripPatternOrdered(rev, tp.id!!)).hasSize(1)
        val reloaded = trips.findByTripId(rev, "T1")!!
        assertThat(reloaded.tripId).isEqualTo("T1")
        assertThat(reloaded.tripPatternId).isEqualTo(tp.id!!)
        assertThat(reloaded.startTimeSec).isEqualTo(28800)
        assertThat(reloaded.endTimeSec).isEqualTo(30600)
        assertThat(scheduleTimes.findByTripOrdered(rev, st.id!!)).hasSize(1)
        assertThat(blocks.findByBlockId(rev, "B1").single().routeIds).isEqualTo(listOf("RA"))
    }
}
