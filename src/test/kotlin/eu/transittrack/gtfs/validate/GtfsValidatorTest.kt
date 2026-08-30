package eu.transittrack.gtfs.validate

import eu.transittrack.gtfs.model.GtfsRoute
import eu.transittrack.gtfs.model.GtfsRouteRepository
import eu.transittrack.gtfs.model.GtfsStop
import eu.transittrack.gtfs.model.GtfsStopRepository
import eu.transittrack.gtfs.model.GtfsStopTime
import eu.transittrack.gtfs.model.GtfsStopTimeRepository
import eu.transittrack.gtfs.model.GtfsTrip
import eu.transittrack.gtfs.model.GtfsTripRepository
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

@PostgresSliceTest
class GtfsValidatorTest(
    @Autowired val dataSource: DataSource,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val trips: GtfsTripRepository,
    @Autowired val stops: GtfsStopRepository,
    @Autowired val stopTimes: GtfsStopTimeRepository,
    @Autowired val routes: GtfsRouteRepository,
) {
    private val validator = GtfsValidator(JdbcTemplate(dataSource))

    private fun newRevision(): Long {
        val feed = feeds.saveAndFlush(
            GtfsFeed(
                "f", "F", null, "http://x/g.zip", null, true, true,
                FeedSource.API, Instant.now(), Instant.now(),
            ),
        )
        return revisions.saveAndFlush(
            GtfsRevision(feedId = feed.id!!, status = GtfsRevisionStatus.VALIDATING, sourceUrl = "x"),
        ).id!!
    }

    private fun stop(rev: Long, id: String) = GtfsStop(
        revisionId = rev, stopId = id, stopCode = null, stopName = id, ttsStopName = null,
        stopDesc = null, stopLat = 51.0, stopLon = 17.0, zoneId = null, stopUrl = null,
        locationType = null, parentStation = null, stopTimezone = null, wheelchairBoarding = null,
        levelId = null, platformCode = null,
    )

    private fun trip(rev: Long, routeId: String, serviceId: String, tripId: String) = GtfsTrip(
        revisionId = rev, routeId = routeId, serviceId = serviceId, tripId = tripId,
        tripHeadsign = null, tripShortName = null, directionId = null, blockId = null,
        shapeId = null, wheelchairAccessible = null, bikesAllowed = null,
    )

    private fun stopTime(rev: Long, tripId: String, seq: Int, stopId: String?) = GtfsStopTime(
        revisionId = rev, tripId = tripId, stopSequence = seq, stopId = stopId,
        arrivalTime = seq * 60, departureTime = seq * 60, locationGroupId = null, locationId = null,
        stopHeadsign = null, startPickupDropOffWindow = null, endPickupDropOffWindow = null,
        pickupType = null, dropOffType = null, continuousPickup = null, continuousDropOff = null,
        shapeDistTraveled = null, timepoint = null, pickupBookingRuleId = null,
        dropOffBookingRuleId = null,
    )

    private fun route(rev: Long, id: String) = GtfsRoute(
        revisionId = rev, routeId = id, agencyId = null, routeShortName = id, routeLongName = null,
        routeDesc = null, routeType = 3, routeUrl = null, routeColor = null, routeTextColor = null,
        routeSortOrder = null, continuousPickup = null, continuousDropOff = null, networkId = null,
    )

    @Test
    fun `flags orphan trip route and orphan stop_time stop`() {
        val rev = newRevision()
        stops.save(stop(rev, "S1"))
        trips.save(trip(rev, "R9", "WK", "T1"))
        stopTimes.save(stopTime(rev, "T1", 1, "S1"))
        stopTimes.saveAndFlush(stopTime(rev, "T1", 2, "S9"))

        val report = validator.validate(rev)
        val rules = report.issues.map { it.rule }

        assertTrue("trip.route_id->route" in rules, "expected orphan trip.route_id, got $rules")
        assertTrue("stop_time.stop_id->stop" in rules, "expected orphan stop_time.stop_id, got $rules")
        assertTrue(report.errorCount >= 2, "expected errorCount >= 2, got ${report.errorCount}")
    }

    @Test
    fun `clean revision produces no errors`() {
        val rev = newRevision()
        routes.saveAndFlush(route(rev, "R1"))

        assertEquals(0, validator.validate(rev).errorCount)
    }
}
