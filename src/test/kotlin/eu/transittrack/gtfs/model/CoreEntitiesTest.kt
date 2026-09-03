package eu.transittrack.gtfs.model

import java.time.Instant
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
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
class CoreEntitiesTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val routes: RouteRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val calendars: CalendarRepository,
) {
    // Real revisions to satisfy the FK gtfs_*.revision_id -> gtfs_revision(id).
    private var rev1: Long = 0
    private var rev2: Long = 0

    @BeforeTest
    fun seedRevisions() {
        val feed =
            feeds.save(
                GtfsFeed(
                    code = "core-test",
                    name = "Core Test",
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
        rev1 =
            revisions
                .save(
                    GtfsRevision(
                        feedId = feed.id!!,
                        status = GtfsRevisionStatus.READY,
                        sourceUrl = "http://x/z.zip",
                        createdAt = Instant.now(),
                    ),
                ).id!!
        rev2 =
            revisions
                .save(
                    GtfsRevision(
                        feedId = feed.id!!,
                        status = GtfsRevisionStatus.SUPERSEDED,
                        sourceUrl = "http://x/z.zip",
                        createdAt = Instant.now(),
                    ),
                ).id!!
    }

    @Test
    fun `entities persist and query by revision`() {
        routes.save(
            Route(
                revisionId = rev1,
                routeId = "R1",
                agencyId = "A",
                routeShortName = "1",
                routeLongName = "Line 1",
                routeDesc = null,
                routeType = 3,
                routeUrl = null,
                routeColor = null,
                routeTextColor = null,
                routeSortOrder = null,
                continuousPickup = null,
                continuousDropOff = null,
                networkId = null,
            ),
        )
        routes.save(
            Route(
                revisionId = rev2,
                routeId = "R1",
                agencyId = "A",
                routeShortName = "1",
                routeLongName = null,
                routeDesc = null,
                routeType = 3,
                routeUrl = null,
                routeColor = null,
                routeTextColor = null,
                routeSortOrder = null,
                continuousPickup = null,
                continuousDropOff = null,
                networkId = null,
            ),
        )

        assertThat(routes.findByRevisionId(rev1)).hasSize(1)
        assertThat(routes.findByRouteId(rev1, "R1")!!.routeLongName).isEqualTo("Line 1")

        stopTimes.save(
            StopTime(
                revisionId = rev1,
                tripId = "T1",
                stopSequence = 1,
                stopId = "S1",
                arrivalTime = 3600,
                departureTime = 3660,
                locationGroupId = null,
                locationId = null,
                stopHeadsign = null,
                startPickupDropOffWindow = null,
                endPickupDropOffWindow = null,
                pickupType = null,
                dropOffType = null,
                continuousPickup = null,
                continuousDropOff = null,
                shapeDistTraveled = null,
                timepoint = null,
                pickupBookingRuleId = null,
                dropOffBookingRuleId = null,
            ),
        )
        assertThat(stopTimes.findByTripId(rev1, "T1")[0].arrivalTime).isEqualTo(3600)

        calendars.save(
            Calendar(
                revisionId = rev1,
                serviceId = "WK",
                monday = true,
                tuesday = true,
                wednesday = true,
                thursday = true,
                friday = true,
                saturday = false,
                sunday = false,
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 12, 31),
            ),
        )
        assertThat(calendars.findByServiceId(rev1, "WK")!!.monday).isEqualTo(true)
    }
}
